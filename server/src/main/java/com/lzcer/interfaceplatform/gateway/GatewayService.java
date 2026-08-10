package com.lzcer.interfaceplatform.gateway;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.lzcer.interfaceplatform.accesscontrol.ExternalApiAuthService;
import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.common.security.PayloadSanitizer;
import com.lzcer.interfaceplatform.interfacecatalog.InterfaceService;
import com.lzcer.interfaceplatform.invocationlog.InvocationLogService;
import com.lzcer.interfaceplatform.sqlquery.SqlApiService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class GatewayService {

    private static final Pattern TRACE_PATTERN = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private final InterfaceService interfaceService;
    private final SqlApiService sqlApiService;
    private final HttpForwardService forwardService;
    private final InvocationLogService logService;
    private final PayloadSanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final ExternalApiAuthService authService;

    public GatewayService(InterfaceService interfaceService, SqlApiService sqlApiService,
                          HttpForwardService forwardService, InvocationLogService logService,
                          PayloadSanitizer sanitizer, ObjectMapper objectMapper,
                          ExternalApiAuthService authService) {
        this.interfaceService = interfaceService;
        this.sqlApiService = sqlApiService;
        this.forwardService = forwardService;
        this.logService = logService;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
        this.authService = authService;
    }

    public GatewayResponse execute(GatewayRequest request) {
        String traceId = traceId(request.headers());
        LocalDateTime startedAt = LocalDateTime.now();
        long timer = System.nanoTime();
        String caller = caller(request);
        String contentType = firstHeader(request.headers(), "content-type");
        String requestSummary = request.body() != null && request.body().length > 0
                ? sanitizer.sanitizeBody(request.body(), contentType)
                : sanitizer.sanitizeBody(jsonBytes(request.queryParameters()), "application/json");
        String requestHeaders = sanitizer.sanitizeHeaders(request.headers());

        // 鉴权失败也写调用日志，但不能进入路由解析和下游系统，避免枚举接口配置。
        ExternalApiAuthService.Caller authenticated;
        try {
            authenticated = authService.authenticate(request);
            caller = authenticated.code();
        } catch (BusinessException exception) {
            return failure(request, traceId, caller, "AUTH", "调用方鉴权", "PLATFORM",
                    "AUTH", null, exception.status(), exception.code(), exception.getMessage(),
                    requestHeaders, requestSummary, startedAt, timer);
        }

        Optional<InterfaceService.RouteConfig> httpRoute = interfaceService.resolve(request.path(), request.method());
        if (httpRoute.isPresent()) {
            try {
                authService.authorize(authenticated, "HTTP", httpRoute.get().code());
            } catch (BusinessException exception) {
                InterfaceService.RouteConfig route = httpRoute.get();
                return failure(request, traceId, caller, route.code(), route.name(), route.targetSystem(),
                        "HTTP", route.targetUrl().toString(), exception.status(), exception.code(), exception.getMessage(),
                        requestHeaders, requestSummary, startedAt, timer);
            }
            if (!httpRoute.get().targetAvailable()) {
                InterfaceService.RouteConfig route = httpRoute.get();
                return failure(request, traceId, caller, route.code(), route.name(), route.targetSystem(),
                        "HTTP", route.targetUrl().toString(), HttpStatus.SERVICE_UNAVAILABLE, "IP-TARGET-002",
                        "目标系统当前不可用", requestHeaders, requestSummary, startedAt, timer);
            }
            return executeHttp(httpRoute.get(), request, traceId, caller, requestHeaders,
                    requestSummary, startedAt, timer);
        }
        Optional<SqlApiService.RuntimeConfig> sqlRoute = sqlApiService.resolve(request.path(), request.method());
        if (sqlRoute.isPresent()) {
            try {
                authService.authorize(authenticated, "SQL", sqlRoute.get().code());
            } catch (BusinessException exception) {
                SqlApiService.RuntimeConfig route = sqlRoute.get();
                return failure(request, traceId, caller, route.code(), route.name(), route.datasourceName(),
                        "SQL", "datasource:" + route.datasourceId(), exception.status(), exception.code(), exception.getMessage(),
                        requestHeaders, requestSummary, startedAt, timer);
            }
            return executeSql(sqlRoute.get(), request, traceId, caller, requestHeaders,
                    requestSummary, startedAt, timer);
        }
        return failure(request, traceId, caller, "UNMATCHED", "未匹配接口", "PLATFORM",
                "ROUTE", null, HttpStatus.NOT_FOUND, "IP-ROUTE-001", "接口未发布或已停用",
                requestHeaders, requestSummary, startedAt, timer);
    }

    public GatewayResponse executeManagementHttp(long interfaceId, String rawQuery,
                                                 Map<String, List<String>> headers, byte[] body,
                                                 String operator) {
        // 管理端“测试调用”已由 Spring Security 保护，因此不使用开放接口的 AppKey/HMAC 协议。
        InterfaceService.RouteConfig route = interfaceService.runtimeConfig(interfaceId);
        GatewayRequest request = new GatewayRequest(route.method(), route.path(), rawQuery, Collections.emptyMap(),
                headers, body, "management-console");
        String traceId = traceId(headers);
        LocalDateTime startedAt = LocalDateTime.now();
        long timer = System.nanoTime();
        String contentType = firstHeader(headers, "content-type");
        String requestSummary = sanitizer.sanitizeBody(body, contentType);
        if (!route.targetAvailable()) {
            return failure(request, traceId, "MANAGEMENT:" + operator, route.code(), route.name(), route.targetSystem(),
                    "HTTP", route.targetUrl().toString(), HttpStatus.SERVICE_UNAVAILABLE, "IP-TARGET-002",
                    "目标系统当前不可用", sanitizer.sanitizeHeaders(headers), requestSummary, startedAt, timer);
        }
        return executeHttp(route, request, traceId, "MANAGEMENT:" + operator,
                sanitizer.sanitizeHeaders(headers), requestSummary, startedAt, timer);
    }

    private GatewayResponse executeHttp(InterfaceService.RouteConfig route, GatewayRequest request,
                                        String traceId, String caller, String requestHeaders,
                                        String requestSummary, LocalDateTime startedAt, long timer) {
        try {
            HttpForwardService.ForwardResult result = forwardService.forward(route,
                    new HttpForwardService.ForwardRequest(request.method(), request.rawQuery(),
                            request.headers(), request.body(), traceId));
            Map<String, List<String>> headers = withTrace(result.headers(), traceId);
            boolean success = result.status() < 400;
            logService.save(new InvocationLogService.InvocationRecord(traceId, "HTTP", route.code(), route.name(),
                    caller, route.targetSystem(), request.method(), request.path(), route.targetUrl().toString(),
                    success ? "SUCCESS" : "FAILED", success ? null : "IP-TARGET-HTTP", result.status(),
                    elapsed(timer), requestHeaders, requestSummary, sanitizer.sanitizeHeaders(headers),
                    sanitizer.sanitizeBody(result.body(), firstHeader(headers, "content-type")),
                    success ? null : "目标系统返回 HTTP " + result.status(), startedAt, LocalDateTime.now()));
            return new GatewayResponse(result.status(), headers, result.body());
        } catch (HttpTimeoutException exception) {
            return failure(request, traceId, caller, route.code(), route.name(), route.targetSystem(), "HTTP",
                    route.targetUrl().toString(), HttpStatus.GATEWAY_TIMEOUT, "IP-TIMEOUT-001",
                    "目标接口调用超时", requestHeaders, requestSummary, startedAt, timer);
        } catch (ConnectException exception) {
            return failure(request, traceId, caller, route.code(), route.name(), route.targetSystem(), "HTTP",
                    route.targetUrl().toString(), HttpStatus.BAD_GATEWAY, "IP-TARGET-001",
                    "目标系统连接失败", requestHeaders, requestSummary, startedAt, timer);
        } catch (BusinessException exception) {
            return failure(request, traceId, caller, route.code(), route.name(), route.targetSystem(), "HTTP",
                    route.targetUrl().toString(), exception.status(), exception.code(), exception.getMessage(),
                    requestHeaders, requestSummary, startedAt, timer);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(request, traceId, caller, route.code(), route.name(), route.targetSystem(), "HTTP",
                    route.targetUrl().toString(), HttpStatus.INTERNAL_SERVER_ERROR, "IP-TARGET-004",
                    "转发任务被中断", requestHeaders, requestSummary, startedAt, timer);
        } catch (IOException | RuntimeException exception) {
            return failure(request, traceId, caller, route.code(), route.name(), route.targetSystem(), "HTTP",
                    route.targetUrl().toString(), HttpStatus.BAD_GATEWAY, "IP-TARGET-001",
                    "目标系统调用失败", requestHeaders, requestSummary, startedAt, timer);
        }
    }

    private GatewayResponse executeSql(SqlApiService.RuntimeConfig route, GatewayRequest request,
                                       String traceId, String caller, String requestHeaders,
                                       String requestSummary, LocalDateTime startedAt, long timer) {
        try {
            Map<String, Object> parameters = parameters(request);
            SqlApiService.QueryResult result = sqlApiService.execute(route, parameters);
            byte[] body = jsonBytes(Map.of("traceId", traceId, "rowCount", result.rowCount(),
                    "maxRows", result.maxRows(), "rows", result.rows()));
            Map<String, List<String>> headers = withTrace(
                    Map.of("content-type", List.of("application/json;charset=UTF-8")), traceId);
            logService.save(new InvocationLogService.InvocationRecord(traceId, "SQL", route.code(), route.name(),
                    caller, route.datasourceName(), request.method(), request.path(), "datasource:" + route.datasourceId(),
                    "SUCCESS", null, 200, elapsed(timer), requestHeaders, requestSummary,
                    sanitizer.sanitizeHeaders(headers), sanitizer.sanitizeBody(body, "application/json"),
                    null, startedAt, LocalDateTime.now()));
            return new GatewayResponse(200, headers, body);
        } catch (BusinessException exception) {
            return failure(request, traceId, caller, route.code(), route.name(), route.datasourceName(), "SQL",
                    "datasource:" + route.datasourceId(), exception.status(), exception.code(), exception.getMessage(),
                    requestHeaders, requestSummary, startedAt, timer);
        } catch (RuntimeException exception) {
            return failure(request, traceId, caller, route.code(), route.name(), route.datasourceName(), "SQL",
                    "datasource:" + route.datasourceId(), HttpStatus.INTERNAL_SERVER_ERROR, "IP-SQL-500",
                    "SQL 查询执行失败", requestHeaders, requestSummary, startedAt, timer);
        }
    }

    private GatewayResponse failure(GatewayRequest request, String traceId, String caller,
                                    String code, String name, String target, String routeType, String targetAddress,
                                    HttpStatus status, String platformCode, String message,
                                    String requestHeaders, String requestSummary,
                                    LocalDateTime startedAt, long timer) {
        byte[] body = jsonBytes(Map.of("success", false, "code", platformCode,
                "message", message == null ? "调用失败" : message, "traceId", traceId));
        Map<String, List<String>> headers = withTrace(
                Map.of("content-type", List.of("application/json;charset=UTF-8")), traceId);
        logService.save(new InvocationLogService.InvocationRecord(traceId, routeType, code, name, caller, target,
                request.method(), request.path(), targetAddress, "FAILED", platformCode, status.value(), elapsed(timer),
                requestHeaders, requestSummary, sanitizer.sanitizeHeaders(headers),
                sanitizer.sanitizeBody(body, "application/json"), message, startedAt, LocalDateTime.now()));
        return new GatewayResponse(status.value(), headers, body);
    }

    private Map<String, Object> parameters(GatewayRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        // 查询参数先装入，JSON 请求体中的同名字段随后覆盖它，便于 POST 显式传递复杂参数。
        request.queryParameters().forEach((name, values) ->
                parameters.put(name, values.size() == 1 ? values.get(0) : values));
        if (request.body() != null && request.body().length > 0) {
            try {
                Map<String, Object> bodyParameters = objectMapper.readValue(request.body(), new TypeReference<>() {});
                parameters.putAll(bodyParameters);
            } catch (JacksonException exception) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "IP-SQL-008", "SQL API 请求体必须是 JSON 对象");
            }
        }
        return parameters;
    }

    private String traceId(Map<String, List<String>> headers) {
        String supplied = firstHeader(headers, "x-trace-id");
        // 仅接受受控字符集和长度，避免把任意外部字符串带入日志和响应头。
        return supplied != null && TRACE_PATTERN.matcher(supplied).matches()
                ? supplied : "T" + UUID.randomUUID().toString().replace("-", "");
    }

    private String caller(GatewayRequest request) {
        String appKey = firstHeader(request.headers(), "x-app-key");
        if (appKey != null && !appKey.isBlank()) return appKey;
        String caller = firstHeader(request.headers(), "x-caller");
        return caller == null || caller.isBlank() ? request.remoteAddress() : caller;
    }

    private String firstHeader(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).filter(values -> !values.isEmpty()).map(values -> values.get(0))
                .findFirst().orElse(null);
    }

    private Map<String, List<String>> withTrace(Map<String, List<String>> source, String traceId) {
        Map<String, List<String>> headers = new LinkedHashMap<>(source);
        headers.keySet().removeIf(name -> name.equalsIgnoreCase("X-Trace-Id"));
        headers.put("X-Trace-Id", List.of(traceId));
        return headers;
    }

    private byte[] jsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JacksonException exception) {
            return "{\"success\":false,\"message\":\"response serialization failed\"}"
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private long elapsed(long timer) {
        return (System.nanoTime() - timer) / 1_000_000;
    }

    public record GatewayRequest(String method, String path, String rawQuery,
                                 Map<String, List<String>> queryParameters,
                                 Map<String, List<String>> headers, byte[] body, String remoteAddress) {
        public GatewayRequest {
            queryParameters = queryParameters == null ? Collections.emptyMap() : queryParameters;
            headers = headers == null ? Collections.emptyMap() : headers;
            body = body == null ? new byte[0] : body;
        }
    }

    public record GatewayResponse(int status, Map<String, List<String>> headers, byte[] body) {}
}
