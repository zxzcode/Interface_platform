package com.lzcer.interfaceplatform.common.security;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PayloadSanitizer {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "x-app-secret",
            "x-signature"
    );
    private static final Pattern SENSITIVE_HEADER = Pattern.compile(
            "(?i).*(authorization|cookie|password|secret|token|api[-_]?key).*"
    );
    private static final Pattern SENSITIVE_FIELD = Pattern.compile(
            "(?i).*(password|passwd|secret|token|authorization|api[_-]?key|id[_-]?(card|no|number)|identity|bank[_-]?(card|account)|phone|mobile).*"
    );
    private static final Pattern TEXT_SECRET = Pattern.compile(
            "(?i)(password|passwd|secret|token|authorization|mobile|phone)(\\s*[=:]\\s*)([^,;\\s&]+)"
    );

    private final ObjectMapper objectMapper;
    private final int maxChars;

    public PayloadSanitizer(ObjectMapper objectMapper,
                            @Value("${platform.forward.max-log-summary-chars:16000}") int maxChars) {
        this.objectMapper = objectMapper;
        this.maxChars = Math.max(256, maxChars);
    }

    public String sanitizeHeaders(Map<String, List<String>> headers) {
        Map<String, Object> safe = new LinkedHashMap<>();
        headers.forEach((name, values) -> safe.put(name,
                SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT)) || SENSITIVE_HEADER.matcher(name).matches()
                        ? "******" : values));
        try {
            return truncate(objectMapper.writeValueAsString(safe));
        } catch (JacksonException exception) {
            return "[headers unavailable]";
        }
    }

    public String sanitizeBody(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return null;
        }
        String normalizedType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!isTextContent(normalizedType)) {
            return "[binary content omitted, " + body.length + " bytes]";
        }
        String text = new String(body, StandardCharsets.UTF_8);
        boolean json = normalizedType.contains("json") || text.stripLeading().startsWith("{") || text.stripLeading().startsWith("[");
        if (json) {
            try {
                JsonNode node = objectMapper.readTree(text);
                redact(node);
                return truncate(objectMapper.writeValueAsString(node));
            } catch (JacksonException ignored) {
                return "[malformed JSON content omitted]";
            }
        }
        if (normalizedType.contains("xml") || text.stripLeading().startsWith("<")) return "[XML content omitted]";
        return truncate(TEXT_SECRET.matcher(text).replaceAll("$1$2******"));
    }

    private boolean isTextContent(String type) {
        return type.isBlank() || type.startsWith("text/") || type.contains("json") || type.contains("xml")
                || type.contains("form") || type.contains("javascript");
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.properties().forEach(entry -> {
                if (SENSITIVE_FIELD.matcher(entry.getKey()).matches()) {
                    objectNode.put(entry.getKey(), "******");
                } else {
                    redact(entry.getValue());
                }
            });
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::redact);
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...[truncated]";
    }
}
