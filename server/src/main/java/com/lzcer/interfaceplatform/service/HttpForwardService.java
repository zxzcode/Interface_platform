package com.lzcer.interfaceplatform.service;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.model.interfacecatalog.InterfaceModels;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class HttpForwardService {

    private static final Set<String> REQUEST_BLOCKED_HEADERS = Set.of(
            "host", "content-length", "connection", "transfer-encoding", "upgrade", "expect", "accept-encoding",
            "authorization", "proxy-authorization", "cookie", "x-app-key", "x-signature", "x-timestamp", "x-nonce",
            "keep-alive", "proxy-connection", "te", "trailer", "forwarded", "via", "x-forwarded-for",
            "x-forwarded-host", "x-forwarded-proto", "x-forwarded-port", "x-real-ip"
    );
    private static final Set<String> RESPONSE_BLOCKED_HEADERS = Set.of(
            "content-length", "connection", "transfer-encoding", "upgrade", "set-cookie"
    );
    private final int maxBodyBytes;

    public HttpForwardService(@Value("${platform.forward.max-body-bytes:1048576}") int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public ForwardResult forward(InterfaceModels.RouteConfig route, ForwardRequest input)
            throws IOException, InterruptedException {
        if (input.body() != null && input.body().length > maxBodyBytes) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "IP-REQUEST-001",
                    "请求体超过平台允许的最大大小");
        }
        URI target = appendQuery(route.targetUrl(), input.rawQuery());
        // 不自动跟随重定向，防止已通过白名单校验的地址跳转到非受控目标。
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(route.connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMillis(route.readTimeoutMs()));
        // 移除连接级、鉴权和代理伪造头；平台只透传业务头并统一注入 Trace ID。
        input.headers().forEach((name, values) -> {
            if (!REQUEST_BLOCKED_HEADERS.contains(name.toLowerCase(Locale.ROOT))
                    && !name.equalsIgnoreCase("X-Trace-Id")) {
                values.forEach(value -> request.header(name, value));
            }
        });
        request.header("X-Trace-Id", input.traceId());
        HttpRequest.BodyPublisher publisher = input.body() == null || input.body().length == 0
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(input.body());
        request.method(input.method(), publisher);

        HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        byte[] responseBody;
        try (InputStream stream = response.body()) {
            responseBody = stream.readNBytes(maxBodyBytes + 1);
        }
        // 请求和响应使用同一上限，防止代理接口被大报文拖垮。
        if (responseBody.length > maxBodyBytes) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "IP-TARGET-003",
                    "目标响应超过平台允许的最大大小");
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> {
            if (!RESPONSE_BLOCKED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) headers.put(name, values);
        });
        return new ForwardResult(response.statusCode(), headers, responseBody, target);
    }

    private URI appendQuery(URI target, String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return target;
        String separator = target.getRawQuery() == null ? "?" : "&";
        return URI.create(target.toString() + separator + rawQuery);
    }

    public record ForwardRequest(String method, String rawQuery, Map<String, List<String>> headers,
                                 byte[] body, String traceId) {}

    public record ForwardResult(int status, Map<String, List<String>> headers, byte[] body, URI target) {}
}
