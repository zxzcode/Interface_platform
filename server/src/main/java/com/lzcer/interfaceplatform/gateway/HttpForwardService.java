package com.lzcer.interfaceplatform.gateway;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.interfacecatalog.InterfaceService;
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
            "authorization", "cookie", "x-app-key", "x-signature", "x-timestamp", "x-nonce"
    );
    private static final Set<String> RESPONSE_BLOCKED_HEADERS = Set.of(
            "content-length", "connection", "transfer-encoding", "upgrade", "set-cookie"
    );
    private final int maxBodyBytes;

    public HttpForwardService(@Value("${platform.forward.max-body-bytes:1048576}") int maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public ForwardResult forward(InterfaceService.RouteConfig route, ForwardRequest input)
            throws IOException, InterruptedException {
        URI target = appendQuery(route.targetUrl(), input.rawQuery());
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(route.connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMillis(route.readTimeoutMs()));
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
