package com.lzcer.interfaceplatform.accesscontrol;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.common.security.CredentialCipher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ApiClientService {

    private static final Set<String> ROUTE_TYPES = Set.of("HTTP", "SQL");
    private final JdbcClient jdbcClient;
    private final CredentialCipher cipher;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiClientService(JdbcClient jdbcClient, CredentialCipher cipher) {
        this.jdbcClient = jdbcClient;
        this.cipher = cipher;
    }

    public List<ClientView> list() {
        return jdbcClient.sql("""
                select id, client_code, client_name, app_key, enabled, created_at, updated_at
                  from ip_api_client order by updated_at desc
                """).query(ApiClientService::mapClient).list().stream()
                .map(value -> value.withPermissions(permissions(value.id()))).toList();
    }

    public ClientView get(long id) {
        ClientView view = find(id);
        if (view == null) throw notFound(id);
        return view.withPermissions(findPermissions(id));
    }

    public List<Permission> permissions(long id) {
        require(id);
        return findPermissions(id);
    }

    @Transactional
    public ClientSecretView create(CreateClientCommand command) {
        List<Permission> permissions = validatePermissions(command.permissions());
        String appKey = "ak_" + random(18);
        String appSecret = "sk_" + random(32);
        jdbcClient.sql("""
                insert into ip_api_client(client_code, client_name, app_key, encrypted_app_secret, enabled)
                values (:code, :name, :appKey, :secret, :enabled)
                """).param("code", normalizeCode(command.code())).param("name", command.name().strip())
                .param("appKey", appKey).param("secret", cipher.encrypt(appSecret))
                .param("enabled", command.enabled()).update();
        ClientView saved = jdbcClient.sql("""
                select id, client_code, client_name, app_key, enabled, created_at, updated_at
                  from ip_api_client where app_key = :appKey
                """).param("appKey", appKey).query(ApiClientService::mapClient).single();
        replacePermissions(saved.id(), permissions);
        return new ClientSecretView(get(saved.id()), appSecret);
    }

    @Transactional
    public ClientView update(long id, UpdateClientCommand command) {
        require(id);
        List<Permission> permissions = validatePermissions(command.permissions());
        jdbcClient.sql("""
                update ip_api_client set client_name = :name, enabled = :enabled,
                       updated_at = current_timestamp where id = :id
                """).param("name", command.name().strip()).param("enabled", command.enabled())
                .param("id", id).update();
        replacePermissions(id, permissions);
        return get(id);
    }

    @Transactional
    public ClientView updatePermissions(long id, List<Permission> permissions) {
        require(id);
        replacePermissions(id, validatePermissions(permissions));
        return get(id);
    }

    @Transactional
    public ClientSecretView rotateSecret(long id) {
        require(id);
        String secret = "sk_" + random(32);
        jdbcClient.sql("""
                update ip_api_client set encrypted_app_secret = :secret,
                       updated_at = current_timestamp where id = :id
                """).param("secret", cipher.encrypt(secret)).param("id", id).update();
        jdbcClient.sql("delete from ip_api_nonce where client_id = :id").param("id", id).update();
        return new ClientSecretView(get(id), secret);
    }

    @Transactional
    public void delete(long id) {
        int changed = jdbcClient.sql("delete from ip_api_client where id = :id").param("id", id).update();
        if (changed == 0) throw notFound(id);
    }

    public AuthenticatedClient findEnabledByAppKey(String appKey) {
        return jdbcClient.sql("""
                select id, client_code, client_name, app_key, encrypted_app_secret
                  from ip_api_client where app_key = :appKey and enabled = true
                """).param("appKey", appKey).query((rs, rowNum) -> new AuthenticatedClient(
                        rs.getLong("id"), rs.getString("client_code"), rs.getString("client_name"),
                        rs.getString("app_key"), cipher.decrypt(rs.getString("encrypted_app_secret"))))
                .optional().orElse(null);
    }

    public boolean isAuthorized(long clientId, String routeType, String resourceCode) {
        return jdbcClient.sql("""
                select count(*) from ip_client_permission
                 where client_id = :clientId and route_type = :routeType and resource_code = :resourceCode
                """).param("clientId", clientId).param("routeType", routeType)
                .param("resourceCode", resourceCode).query(Long.class).single() > 0;
    }

    private List<Permission> findPermissions(long clientId) {
        return jdbcClient.sql("""
                select route_type, resource_code from ip_client_permission
                 where client_id = :id order by route_type, resource_code
                """).param("id", clientId).query((rs, rowNum) ->
                new Permission(rs.getString("route_type"), rs.getString("resource_code"))).list();
    }

    private List<Permission> validatePermissions(List<Permission> raw) {
        List<Permission> values = raw == null ? List.of() : raw.stream().map(permission -> {
            String type = permission.routeType() == null ? "" : permission.routeType().strip().toUpperCase(Locale.ROOT);
            String code = normalizeCode(permission.resourceCode());
            if (!ROUTE_TYPES.contains(type)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-CLIENT-002", "资源类型只支持 HTTP 或 SQL");
            }
            String table = "HTTP".equals(type) ? "ip_interface" : "ip_sql_api";
            String column = "HTTP".equals(type) ? "interface_code" : "api_code";
            long count = jdbcClient.sql("select count(*) from " + table + " where " + column + " = :code")
                    .param("code", code).query(Long.class).single();
            if (count == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-CLIENT-003", "授权资源不存在: " + type + "/" + code);
            return new Permission(type, code);
        }).distinct().toList();
        return values;
    }

    private void replacePermissions(long clientId, List<Permission> permissions) {
        jdbcClient.sql("delete from ip_client_permission where client_id = :id").param("id", clientId).update();
        permissions.forEach(permission -> jdbcClient.sql("""
                insert into ip_client_permission(client_id, route_type, resource_code)
                values (:clientId, :routeType, :resourceCode)
                """).param("clientId", clientId).param("routeType", permission.routeType())
                .param("resourceCode", permission.resourceCode()).update());
    }

    private ClientView find(long id) {
        return jdbcClient.sql("""
                select id, client_code, client_name, app_key, enabled, created_at, updated_at
                  from ip_api_client where id = :id
                """).param("id", id).query(ApiClientService::mapClient).optional().orElse(null);
    }

    private void require(long id) { if (find(id) == null) throw notFound(id); }

    private String normalizeCode(String code) {
        String value = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9_-]{2,80}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-CLIENT-004", "编码只能包含字母、数字、下划线和短横线");
        }
        return value;
    }

    private String random(int bytes) {
        byte[] value = new byte[bytes];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private BusinessException notFound(long id) {
        return new BusinessException(HttpStatus.NOT_FOUND, "IP-CLIENT-404", "调用方不存在: " + id);
    }

    private static ClientView mapClient(ResultSet rs, int rowNum) throws SQLException {
        return new ClientView(rs.getLong("id"), rs.getString("client_code"), rs.getString("client_name"),
                rs.getString("app_key"), rs.getBoolean("enabled"), List.of(),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class));
    }

    public record Permission(@NotBlank String routeType, @NotBlank String resourceCode) {}
    public record CreateClientCommand(@NotBlank @Size(max = 80) String code,
                                      @NotBlank @Size(max = 160) String name,
                                      boolean enabled, List<@Valid Permission> permissions) {}
    public record UpdateClientCommand(@NotBlank @Size(max = 160) String name,
                                      boolean enabled, List<@Valid Permission> permissions) {}
    public record ClientView(long id, String code, String name, String appKey, boolean enabled,
                             List<Permission> permissions, LocalDateTime createdAt, LocalDateTime updatedAt) {
        ClientView withPermissions(List<Permission> value) {
            return new ClientView(id, code, name, appKey, enabled, value, createdAt, updatedAt);
        }
    }
    public record ClientSecretView(ClientView client, String appSecret) {}
    public record AuthenticatedClient(long id, String code, String name, String appKey, String appSecret) {}
}
