package com.lzcer.interfaceplatform.interfacecatalog;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SystemService {
    private static final Set<String> STATUSES = Set.of("ONLINE", "DEGRADED", "OFFLINE", "UNKNOWN");
    private final JdbcClient jdbcClient;

    public SystemService(JdbcClient jdbcClient) { this.jdbcClient = jdbcClient; }

    public List<SystemView> list() {
        return jdbcClient.sql("""
                select id, system_code, system_name, base_url, health_status, created_at, updated_at
                  from ip_system order by system_code
                """).query(SystemService::map).list();
    }

    public SystemView get(long id) {
        return jdbcClient.sql("""
                select id, system_code, system_name, base_url, health_status, created_at, updated_at
                  from ip_system where id = :id
                """).param("id", id).query(SystemService::map).optional().orElseThrow(() -> notFound(id));
    }

    @Transactional
    public SystemView create(SystemCommand command) {
        Validated value = validate(command);
        jdbcClient.sql("""
                insert into ip_system(system_code, system_name, base_url, health_status)
                values (:code, :name, :baseUrl, :status)
                """).param("code", value.code()).param("name", value.name())
                .param("baseUrl", value.baseUrl()).param("status", value.status()).update();
        return list().stream().filter(item -> item.code().equals(value.code())).findFirst().orElseThrow();
    }

    @Transactional
    public SystemView update(long id, SystemCommand command) {
        get(id);
        Validated value = validate(command);
        jdbcClient.sql("""
                update ip_system set system_code = :code, system_name = :name, base_url = :baseUrl,
                       health_status = :status, updated_at = current_timestamp where id = :id
                """).param("code", value.code()).param("name", value.name())
                .param("baseUrl", value.baseUrl()).param("status", value.status()).param("id", id).update();
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        int changed = jdbcClient.sql("delete from ip_system where id = :id").param("id", id).update();
        if (changed == 0) throw notFound(id);
    }

    private Validated validate(SystemCommand command) {
        String code = command.code().strip().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9_-]{2,40}")) throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-SYSTEM-001", "系统编码格式无效");
        String status = command.status().strip().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-SYSTEM-002", "系统状态无效");
        String baseUrl = command.baseUrl().strip();
        try {
            URI uri = new URI(baseUrl);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getQuery() != null) throw new URISyntaxException(baseUrl, "invalid base url");
        } catch (URISyntaxException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-SYSTEM-003", "基础地址必须是无账号、查询串和片段的 HTTP/HTTPS 地址");
        }
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        return new Validated(code, command.name().strip(), baseUrl, status);
    }

    private static SystemView map(ResultSet rs, int rowNum) throws SQLException {
        return new SystemView(rs.getLong("id"), rs.getString("system_code"), rs.getString("system_name"),
                rs.getString("base_url"), rs.getString("health_status"),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class));
    }

    private BusinessException notFound(long id) { return new BusinessException(HttpStatus.NOT_FOUND, "IP-SYSTEM-404", "系统档案不存在: " + id); }

    public record SystemCommand(@NotBlank @Size(max = 40) String code,
                                @NotBlank @Size(max = 100) String name,
                                @NotBlank @Size(max = 500) String baseUrl,
                                @NotBlank String status) {}
    public record SystemView(long id, String code, String name, String baseUrl, String status,
                             LocalDateTime createdAt, LocalDateTime updatedAt) {}
    private record Validated(String code, String name, String baseUrl, String status) {}
}
