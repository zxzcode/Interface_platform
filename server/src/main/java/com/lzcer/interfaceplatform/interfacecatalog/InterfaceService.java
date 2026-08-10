package com.lzcer.interfaceplatform.interfacecatalog;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class InterfaceService {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private final JdbcClient jdbcClient;

    public InterfaceService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<InterfaceView> list() {
        return jdbcClient.sql("""
                select i.id, i.interface_code, i.interface_name, i.description,
                       i.source_system_id, source_system.system_name as source_system,
                       i.target_system_id, target_system.system_name as target_system,
                       i.http_method, i.interface_path, i.target_url, i.connect_timeout_ms,
                       i.read_timeout_ms, i.enabled, i.updated_at,
                       count(l.id) as today_calls,
                       case when count(l.id) = 0 then 0
                            else sum(case when l.call_status = 'SUCCESS' then 1 else 0 end) * 100.0 / count(l.id)
                       end as success_rate,
                       coalesce(avg(l.duration_ms), 0) as avg_duration_ms
                  from ip_interface i
                  join ip_system source_system on source_system.id = i.source_system_id
                  join ip_system target_system on target_system.id = i.target_system_id
                  left join ip_invocation_log l on l.interface_code = i.interface_code
                       and l.route_type = 'HTTP' and l.call_time >= current_date
                 group by i.id, i.interface_code, i.interface_name, i.description,
                       i.source_system_id, source_system.system_name, i.target_system_id,
                       target_system.system_name, i.http_method, i.interface_path, i.target_url,
                       i.connect_timeout_ms, i.read_timeout_ms, i.enabled, i.updated_at
                 order by i.updated_at desc
                """).query(InterfaceService::mapView).list();
    }

    public InterfaceView get(long id) {
        return list().stream().filter(item -> item.id() == id).findFirst()
                .orElseThrow(() -> notFound(id));
    }

    public List<SystemOption> systems() {
        return jdbcClient.sql("select id, system_code, system_name, base_url, health_status from ip_system order by id")
                .query((rs, rowNum) -> new SystemOption(rs.getLong("id"), rs.getString("system_code"),
                        rs.getString("system_name"), rs.getString("base_url"), rs.getString("health_status")))
                .list();
    }

    @Transactional
    public InterfaceView create(InterfaceCommand command) {
        ValidatedCommand value = validate(command);
        jdbcClient.sql("""
                insert into ip_interface(
                    interface_code, interface_name, description, source_system_id, target_system_id,
                    http_method, interface_path, target_url, connect_timeout_ms, read_timeout_ms, enabled
                ) values (
                    :code, :name, :description, :sourceId, :targetId,
                    :method, :path, :targetUrl, :connectTimeout, :readTimeout, :enabled
                )
                """).param("code", value.code()).param("name", value.name())
                .param("description", value.description()).param("sourceId", value.sourceSystemId())
                .param("targetId", value.targetSystemId()).param("method", value.method())
                .param("path", value.path()).param("targetUrl", value.targetUrl())
                .param("connectTimeout", value.connectTimeoutMs()).param("readTimeout", value.readTimeoutMs())
                .param("enabled", value.enabled()).update();
        return findByCode(value.code());
    }

    @Transactional
    public InterfaceView update(long id, InterfaceCommand command) {
        requireExists(id);
        ValidatedCommand value = validate(command);
        jdbcClient.sql("""
                update ip_interface
                   set interface_code = :code, interface_name = :name, description = :description,
                       source_system_id = :sourceId, target_system_id = :targetId,
                       http_method = :method, interface_path = :path, target_url = :targetUrl,
                       connect_timeout_ms = :connectTimeout, read_timeout_ms = :readTimeout,
                       enabled = :enabled, updated_at = current_timestamp
                 where id = :id
                """).param("code", value.code()).param("name", value.name())
                .param("description", value.description()).param("sourceId", value.sourceSystemId())
                .param("targetId", value.targetSystemId()).param("method", value.method())
                .param("path", value.path()).param("targetUrl", value.targetUrl())
                .param("connectTimeout", value.connectTimeoutMs()).param("readTimeout", value.readTimeoutMs())
                .param("enabled", value.enabled()).param("id", id).update();
        return get(id);
    }

    @Transactional
    public InterfaceView setEnabled(long id, boolean enabled) {
        int updated = jdbcClient.sql("update ip_interface set enabled = :enabled, updated_at = current_timestamp where id = :id")
                .param("enabled", enabled).param("id", id).update();
        if (updated == 0) throw notFound(id);
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        String code = jdbcClient.sql("select interface_code from ip_interface where id = :id")
                .param("id", id).query(String.class).optional().orElseThrow(() -> notFound(id));
        int deleted = jdbcClient.sql("delete from ip_interface where id = :id").param("id", id).update();
        if (deleted == 0) throw notFound(id);
        jdbcClient.sql("delete from ip_client_permission where route_type = 'HTTP' and resource_code = :code")
                .param("code", code).update();
    }

    public Optional<RouteConfig> resolve(String path, String method) {
        return jdbcClient.sql("""
                select i.id, i.interface_code, i.interface_name, i.target_url, i.http_method, i.interface_path,
                       i.connect_timeout_ms, i.read_timeout_ms, target_system.system_name as target_system,
                       target_system.health_status as target_status
                  from ip_interface i
                  join ip_system target_system on target_system.id = i.target_system_id
                 where i.interface_path = :path and i.http_method = :method and i.enabled = true
                """).param("path", path).param("method", method.toUpperCase(Locale.ROOT))
                .query((rs, rowNum) -> new RouteConfig(rs.getLong("id"), rs.getString("interface_code"),
                        rs.getString("interface_name"), rs.getString("target_system"), rs.getString("target_status"),
                        URI.create(rs.getString("target_url")), rs.getInt("connect_timeout_ms"),
                        rs.getInt("read_timeout_ms"), rs.getString("http_method"), rs.getString("interface_path")))
                .optional();
    }

    public RouteConfig runtimeConfig(long id) {
        return jdbcClient.sql("""
                select i.id, i.interface_code, i.interface_name, i.target_url, i.http_method, i.interface_path,
                       i.connect_timeout_ms, i.read_timeout_ms, target_system.system_name as target_system,
                       target_system.health_status as target_status
                  from ip_interface i join ip_system target_system on target_system.id = i.target_system_id
                 where i.id = :id
                """).param("id", id).query((rs, rowNum) -> new RouteConfig(rs.getLong("id"),
                        rs.getString("interface_code"), rs.getString("interface_name"),
                        rs.getString("target_system"), rs.getString("target_status"), URI.create(rs.getString("target_url")),
                        rs.getInt("connect_timeout_ms"), rs.getInt("read_timeout_ms"),
                        rs.getString("http_method"), rs.getString("interface_path")))
                .optional().orElseThrow(() -> notFound(id));
    }

    private InterfaceView findByCode(String code) {
        return list().stream().filter(item -> item.code().equals(code)).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "IP-CONFIG-001", "接口保存后无法读取"));
    }

    private void requireExists(long id) {
        if (jdbcClient.sql("select count(*) from ip_interface where id = :id").param("id", id)
                .query(Long.class).single() == 0) {
            throw notFound(id);
        }
    }

    private String requireSystem(long id) {
        return jdbcClient.sql("select base_url from ip_system where id = :id").param("id", id)
                .query(String.class).optional().orElseThrow(() -> new BusinessException(
                        HttpStatus.BAD_REQUEST, "IP-CONFIG-002", "系统档案不存在或未配置基础地址: " + id));
    }

    private ValidatedCommand validate(InterfaceCommand command) {
        String method = command.method().strip().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-CONFIG-003", "不支持的 HTTP 方法");
        }
        String path = normalizePath(command.path());
        String targetUrl = command.targetUrl().strip();
        requireSystem(command.sourceSystemId());
        String targetBaseUrl = requireSystem(command.targetSystemId());
        validateTargetUrl(targetUrl, targetBaseUrl);
        return new ValidatedCommand(command.code().strip().toUpperCase(Locale.ROOT), command.name().strip(),
                blankToNull(command.description()), command.sourceSystemId(), command.targetSystemId(), method,
                path, targetUrl, command.connectTimeoutMs(), command.readTimeoutMs(), command.enabled());
    }

    private String normalizePath(String rawPath) {
        String path = rawPath.strip();
        if (!path.startsWith("/open-api/") || path.contains("?") || path.contains("#") || path.contains("..")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-CONFIG-004",
                    "开放路径必须以 /open-api/ 开头，且不能包含查询串、片段或上级路径");
        }
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private void validateTargetUrl(String value, String baseUrl) {
        try {
            URI uri = new URI(value);
            URI base = new URI(baseUrl);
            // 接口目标只能落在所属系统的“协议 + 主机 + 端口 + 基础路径”白名单内，避免配置被用作 SSRF 跳板。
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getQuery() != null || !isWithinBasePath(uri, base)) {
                throw new URISyntaxException(value, "target URL is not allowed");
            }
            if (!uri.getScheme().equalsIgnoreCase(base.getScheme())
                    || !uri.getHost().equalsIgnoreCase(base.getHost())
                    || effectivePort(uri) != effectivePort(base)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-TARGET-005",
                        "目标地址必须与目标系统基础地址使用相同的协议、主机和端口");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (URISyntaxException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-TARGET-002",
                    "目标地址必须是无账号信息和片段的 HTTP/HTTPS 地址");
        }
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private boolean isWithinBasePath(URI target, URI base) {
        String targetPath = normalizedPath(target.getRawPath());
        String basePath = normalizedPath(base.getRawPath());
        // 同源还不够：/sap-api 不能访问 /admin；编码后的路径控制字符也必须拒绝。
        if (containsEncodedPathControl(targetPath) || containsEncodedPathControl(basePath)) return false;
        return "/".equals(basePath) || targetPath.equals(basePath) || targetPath.startsWith(basePath + "/");
    }

    private String normalizedPath(String path) {
        if (path == null || path.isBlank()) return "/";
        String value = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        return value.startsWith("/") ? value : "/" + value;
    }

    private boolean containsEncodedPathControl(String path) {
        String value = path.toLowerCase(Locale.ROOT);
        return value.contains("/../") || value.endsWith("/..") || value.contains("/./") || value.endsWith("/.")
                || value.contains("%2e") || value.contains("%2f") || value.contains("%5c") || value.contains("\\");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private BusinessException notFound(long id) {
        return new BusinessException(HttpStatus.NOT_FOUND, "IP-CONFIG-404", "接口配置不存在: " + id);
    }

    private static InterfaceView mapView(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal successRate = rs.getBigDecimal("success_rate");
        return new InterfaceView(rs.getLong("id"), rs.getString("interface_code"),
                rs.getString("interface_name"), rs.getString("description"), rs.getLong("source_system_id"),
                rs.getString("source_system"), rs.getLong("target_system_id"), rs.getString("target_system"),
                rs.getString("http_method"), rs.getString("interface_path"), rs.getString("target_url"),
                rs.getInt("connect_timeout_ms"), rs.getInt("read_timeout_ms"), rs.getBoolean("enabled"),
                rs.getLong("today_calls"), successRate == null ? BigDecimal.ZERO : successRate,
                rs.getLong("avg_duration_ms"), rs.getObject("updated_at", LocalDateTime.class));
    }

    public record InterfaceCommand(
            @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description,
            @NotNull Long sourceSystemId,
            @NotNull Long targetSystemId,
            @NotBlank String method,
            @NotBlank @Size(max = 300) String path,
            @NotBlank @Size(max = 1000) String targetUrl,
            @Min(500) @Max(30000) int connectTimeoutMs,
            @Min(500) @Max(120000) int readTimeoutMs,
            boolean enabled
    ) {}

    public record InterfaceView(long id, String code, String name, String description,
                                long sourceSystemId, String sourceSystem, long targetSystemId, String targetSystem,
                                String method, String path, String targetUrl, int connectTimeoutMs,
                                int readTimeoutMs, boolean enabled, long todayCalls, BigDecimal successRate,
                                long averageDurationMs, LocalDateTime updatedAt) {}

    public record SystemOption(long id, String code, String name, String baseUrl, String status) {}

    public record RouteConfig(long id, String code, String name, String targetSystem, String targetStatus, URI targetUrl,
                              int connectTimeoutMs, int readTimeoutMs, String method, String path) {
        public boolean targetAvailable() {
            return !"OFFLINE".equalsIgnoreCase(targetStatus);
        }
    }

    private record ValidatedCommand(String code, String name, String description, long sourceSystemId,
                                    long targetSystemId, String method, String path, String targetUrl,
                                    int connectTimeoutMs, int readTimeoutMs, boolean enabled) {}
}
