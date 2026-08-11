package com.lzcer.interfaceplatform.interfacecatalog;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SystemService {
    private static final Set<String> STATUSES = Set.of("ONLINE", "DEGRADED", "OFFLINE", "UNKNOWN");
    private final SystemMapper systemMapper;

    public SystemService(SystemMapper systemMapper) { this.systemMapper = systemMapper; }

    public List<SystemView> list() {
        return systemMapper.findAll().stream().map(SystemService::toView).toList();
    }

    public SystemView get(long id) {
        SystemMapper.SystemRow row = systemMapper.findById(id);
        if (row == null) throw notFound(id);
        return toView(row);
    }

    @Transactional
    public SystemView create(SystemCommand command) {
        Validated value = validate(command);
        systemMapper.insert(value.code(), value.name(), value.baseUrl(), value.status());
        return list().stream().filter(item -> item.code().equals(value.code())).findFirst().orElseThrow();
    }

    @Transactional
    public SystemView update(long id, SystemCommand command) {
        get(id);
        Validated value = validate(command);
        // 系统基础地址是该系统接口目标地址的白名单边界，修改前必须复核已配置接口。
        ensureReferencedTargetsRemainAllowed(id, value.baseUrl());
        systemMapper.update(id, value.code(), value.name(), value.baseUrl(), value.status());
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        try {
            int changed = systemMapper.delete(id);
            if (changed == 0) throw notFound(id);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-SYSTEM-004", "被接口引用的系统档案不能删除");
        }
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
                    || uri.getQuery() != null || containsEncodedPathControl(uri.getRawPath())) {
                throw new URISyntaxException(baseUrl, "invalid base url");
            }
        } catch (URISyntaxException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-SYSTEM-003", "基础地址必须是无账号、查询串和片段的 HTTP/HTTPS 地址");
        }
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        return new Validated(code, command.name().strip(), baseUrl, status);
    }

    private void ensureReferencedTargetsRemainAllowed(long systemId, String baseUrl) {
        URI base = URI.create(baseUrl);
        List<String> targets = systemMapper.findReferencedTargetUrls(systemId);
        for (String targetUrl : targets) {
            try {
                URI target = URI.create(targetUrl);
                if (!sameOrigin(target, base) || !isWithinBasePath(target, base)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-SYSTEM-005",
                            "已引用接口的目标地址不在新的系统基础地址白名单范围内");
                }
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-SYSTEM-005",
                        "已引用接口的目标地址不在新的系统基础地址白名单范围内");
            }
        }
    }

    private boolean sameOrigin(URI target, URI base) {
        return target.getScheme() != null && target.getScheme().equalsIgnoreCase(base.getScheme())
                && target.getHost() != null && target.getHost().equalsIgnoreCase(base.getHost())
                && effectivePort(target) == effectivePort(base);
    }

    private boolean isWithinBasePath(URI target, URI base) {
        String targetPath = normalizedPath(target.getRawPath());
        String basePath = normalizedPath(base.getRawPath());
        return !containsEncodedPathControl(targetPath) && !containsEncodedPathControl(basePath)
                && ("/".equals(basePath) || targetPath.equals(basePath) || targetPath.startsWith(basePath + "/"));
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String normalizedPath(String path) {
        if (path == null || path.isBlank()) return "/";
        String value = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        return value.startsWith("/") ? value : "/" + value;
    }

    private boolean containsEncodedPathControl(String path) {
        String value = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return value.contains("/../") || value.endsWith("/..") || value.contains("/./") || value.endsWith("/.")
                || value.contains("%2e") || value.contains("%2f") || value.contains("%5c") || value.contains("\\");
    }

    private static SystemView toView(SystemMapper.SystemRow row) {
        return new SystemView(row.id(), row.code(), row.name(), row.baseUrl(), row.status(),
                row.createdAt(), row.updatedAt());
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
