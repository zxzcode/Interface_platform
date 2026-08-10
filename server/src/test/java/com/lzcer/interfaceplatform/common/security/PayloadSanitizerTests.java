package com.lzcer.interfaceplatform.common.security;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadSanitizerTests {

    private final PayloadSanitizer sanitizer = new PayloadSanitizer(new ObjectMapper(), 256);

    @Test
    void redactsSensitiveHeadersAndKeepsOperationalHeaders() {
        String result = sanitizer.sanitizeHeaders(Map.of(
                "Authorization", List.of("Bearer top-secret-token"),
                "X-Signature", List.of("signature-value"),
                "X-Trace-Id", List.of("trace-12345678")));

        assertThat(result).contains("X-Trace-Id", "trace-12345678", "******")
                .doesNotContain("top-secret-token", "signature-value");
    }

    @Test
    void redactsNestedJsonAndFormSensitiveValuesBeforeLogging() {
        String json = sanitizer.sanitizeBody("{\"orderNo\":\"SO-1\",\"password\":\"db-password\",\"nested\":{\"appSecret\":\"client-secret\",\"phone\":\"13800138000\"}}"
                .getBytes(StandardCharsets.UTF_8), "application/json");
        String form = sanitizer.sanitizeBody("username=demo&password=form-password&token=form-token"
                .getBytes(StandardCharsets.UTF_8), "application/x-www-form-urlencoded");

        assertThat(json).contains("SO-1", "******")
                .doesNotContain("db-password", "client-secret", "13800138000");
        assertThat(form).contains("password=******", "token=******")
                .doesNotContain("form-password", "form-token");
    }

    @Test
    void omitsBinaryContentAndTruncatesOversizedText() {
        String binary = sanitizer.sanitizeBody(new byte[]{0, 1, 2, 3}, "application/octet-stream");
        String text = sanitizer.sanitizeBody("x".repeat(266).getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(binary).contains("binary content omitted", "4 bytes");
        assertThat(text).endsWith("...[truncated]").hasSize(270);
    }
}
