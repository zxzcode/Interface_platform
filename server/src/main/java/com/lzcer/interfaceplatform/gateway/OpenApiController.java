package com.lzcer.interfaceplatform.gateway;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class OpenApiController {

    private final GatewayService gatewayService;
    private final int maxBodyBytes;

    public OpenApiController(GatewayService gatewayService,
                             @Value("${platform.forward.max-body-bytes:1048576}") int maxBodyBytes) {
        this.gatewayService = gatewayService;
        this.maxBodyBytes = maxBodyBytes;
    }

    @RequestMapping("/open-api/**")
    public ResponseEntity<byte[]> execute(HttpServletRequest servletRequest,
                                          @RequestBody(required = false) byte[] body) {
        byte[] requestBody = body == null ? new byte[0] : body;
        if (requestBody.length > maxBodyBytes) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "IP-REQUEST-001", "请求体超过平台允许的最大大小");
        }
        GatewayService.GatewayRequest request = new GatewayService.GatewayRequest(
                servletRequest.getMethod(), normalizePath(servletRequest.getRequestURI()),
                servletRequest.getQueryString(), queryParameters(servletRequest), headers(servletRequest),
                requestBody, servletRequest.getRemoteAddr());
        GatewayService.GatewayResponse response = gatewayService.execute(request);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status());
        response.headers().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        return builder.body(response.body());
    }

    private String normalizePath(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private Map<String, List<String>> headers(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name ->
                headers.put(name, Collections.list(request.getHeaders(name))));
        return headers;
    }

    private Map<String, List<String>> queryParameters(HttpServletRequest request) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        String rawQuery = request.getQueryString();
        if (rawQuery != null && !rawQuery.isBlank()) {
            UriComponentsBuilder.fromUriString("http://localhost/?" + rawQuery).build()
                    .getQueryParams().forEach((name, values) -> parameters.put(name, List.copyOf(values)));
        }
        return parameters;
    }
}
