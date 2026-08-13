package com.lzcer.interfaceplatform.accesscontrol;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.common.security.CredentialCipher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ApiClientService {

    private static final Set<String> ROUTE_TYPES = Set.of("HTTP", "SQL");
    private final ApiClientMapper apiClientMapper;
    private final CredentialCipher cipher;
    private final ApiNonceMapper nonceMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiClientService(ApiClientMapper apiClientMapper, CredentialCipher cipher, ApiNonceMapper nonceMapper) {
        this.apiClientMapper = apiClientMapper;
        this.cipher = cipher;
        this.nonceMapper = nonceMapper;
    }

    public List<ClientView> list() {
        return apiClientMapper.findAll().stream().map(ApiClientService::toView)
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
        // AppSecret 只在创建和轮换时明文返回；数据库中始终只保存加密值。
        String appSecret = "sk_" + random(32);
        apiClientMapper.insert(normalizeCode(command.code()), command.name().strip(), appKey, cipher.encrypt(appSecret), command.enabled());
        ClientView saved = toView(apiClientMapper.findByAppKey(appKey));
        replacePermissions(saved.id(), permissions);
        return new ClientSecretView(get(saved.id()), appSecret);
    }

    @Transactional
    public ClientView update(long id, UpdateClientCommand command) {
        require(id);
        List<Permission> permissions = validatePermissions(command.permissions());
        apiClientMapper.update(id, command.name().strip(), command.enabled());
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
        apiClientMapper.updateSecret(id, cipher.encrypt(secret));
        // 轮换后清理旧 Nonce，避免旧凭证请求占用新凭证的防重放窗口。
        // Nonce 与密钥生命周期绑定，轮换密钥后清除旧窗口中的记录。
        nonceMapper.deleteByClientId(id);
        return new ClientSecretView(get(id), secret);
    }

    @Transactional
    public void delete(long id) {
        int changed = apiClientMapper.delete(id);
        if (changed == 0) throw notFound(id);
    }

    public AuthenticatedClient findEnabledByAppKey(String appKey) {
        ApiClientMapper.SecretRow row = apiClientMapper.findEnabledSecretByAppKey(appKey);
        return row == null ? null : new AuthenticatedClient(row.id(), row.code(), row.name(), row.appKey(), cipher.decrypt(row.encryptedSecret()));
    }

    public boolean isAuthorized(long clientId, String routeType, String resourceCode) {
        return apiClientMapper.countPermission(clientId, routeType, resourceCode) > 0;
    }

    private List<Permission> findPermissions(long clientId) {
        return apiClientMapper.findPermissions(clientId);
    }

    private List<Permission> validatePermissions(List<Permission> raw) {
        List<Permission> values = raw == null ? List.of() : raw.stream().map(permission -> {
            String type = permission.routeType() == null ? "" : permission.routeType().strip().toUpperCase(Locale.ROOT);
            String code = normalizeCode(permission.resourceCode());
            if (!ROUTE_TYPES.contains(type)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-CLIENT-002", "资源类型只支持 HTTP 或 SQL");
            }
            long count = apiClientMapper.countResource(type, code);
            if (count == 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-CLIENT-003", "授权资源不存在: " + type + "/" + code);
            return new Permission(type, code);
        }).distinct().toList();
        return values;
    }

    private void replacePermissions(long clientId, List<Permission> permissions) {
        apiClientMapper.deletePermissions(clientId);
        permissions.forEach(permission -> apiClientMapper.insertPermission(clientId, permission.routeType(), permission.resourceCode()));
    }

    private ClientView find(long id) {
        ApiClientMapper.ClientRow row = apiClientMapper.findById(id);
        return row == null ? null : toView(row);
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

    private static ClientView toView(ApiClientMapper.ClientRow row) {
        return new ClientView(row.id(), row.code(), row.name(), row.appKey(), row.enabled(), List.of(), row.createdAt(), row.updatedAt());
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
