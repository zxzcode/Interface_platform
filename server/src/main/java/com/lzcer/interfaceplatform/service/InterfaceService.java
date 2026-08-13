package com.lzcer.interfaceplatform.service;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.mapper.InterfaceMapper;
import com.lzcer.interfaceplatform.mapper.SystemMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class InterfaceService {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private final InterfaceMapper interfaceMapper;
    private final SystemMapper systemMapper;

    public InterfaceService(InterfaceMapper interfaceMapper, SystemMapper systemMapper) {
        this.interfaceMapper = interfaceMapper;
        this.systemMapper = systemMapper;
    }

    public List<InterfaceView> list() {
        return interfaceMapper.findAll();
    }

    public InterfaceView get(long id) {
        InterfaceView value = interfaceMapper.findById(id);
        if (value == null) throw notFound(id);
        return value;
    }

    public List<SystemOption> systems() {
        return interfaceMapper.findSystemOptions();
    }

    @Transactional
    public InterfaceView create(InterfaceCommand command) {
        ValidatedCommand value = validate(command);
        interfaceMapper.insert(value.code(), value.name(), value.description(), value.sourceSystemId(), value.targetSystemId(), value.method(), value.path(), value.targetUrl(), value.connectTimeoutMs(), value.readTimeoutMs(), value.enabled());
        return findByCode(value.code());
    }

    @Transactional
    public InterfaceView update(long id, InterfaceCommand command) {
        requireExists(id);
        ValidatedCommand value = validate(command);
        interfaceMapper.update(id, value.code(), value.name(), value.description(), value.sourceSystemId(), value.targetSystemId(), value.method(), value.path(), value.targetUrl(), value.connectTimeoutMs(), value.readTimeoutMs(), value.enabled());
        return get(id);
    }

    @Transactional
    public InterfaceView setEnabled(long id, boolean enabled) {
        int updated = interfaceMapper.updateEnabled(id, enabled);
        if (updated == 0) throw notFound(id);
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        InterfaceView configured = interfaceMapper.findById(id);
        if (configured == null) throw notFound(id);
        int deleted = interfaceMapper.delete(id);
        if (deleted == 0) throw notFound(id);
        interfaceMapper.deleteHttpPermissions(configured.code());
    }

    public Optional<RouteConfig> resolve(String path, String method) {
        return Optional.ofNullable(interfaceMapper.findEnabledByPathAndMethod(path, method.toUpperCase(Locale.ROOT)))
                .map(this::toRouteConfig);
    }

    public RouteConfig runtimeConfig(long id) {
        InterfaceMapper.RouteRow row = interfaceMapper.findRuntimeConfig(id);
        if (row == null) throw notFound(id);
        return toRouteConfig(row);
    }

    private RouteConfig toRouteConfig(InterfaceMapper.RouteRow row) {
        return new RouteConfig(row.id(), row.code(), row.name(), row.targetSystem(), row.targetStatus(),
                URI.create(row.targetUrl()), row.connectTimeoutMs(), row.readTimeoutMs(), row.method(), row.path());
    }

    private InterfaceView findByCode(String code) {
        return list().stream().filter(item -> item.code().equals(code)).findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "IP-CONFIG-001", "接口保存后无法读取"));
    }

    private void requireExists(long id) {
        if (interfaceMapper.findById(id) == null) throw notFound(id);
    }

    private String requireSystem(long id) {
        SystemMapper.SystemRow system = systemMapper.findById(id);
        if (system == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-CONFIG-002",
                    "System record does not exist or has no base URL: " + id);
        }
        return system.baseUrl();
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
