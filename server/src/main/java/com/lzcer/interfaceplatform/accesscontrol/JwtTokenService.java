package com.lzcer.interfaceplatform.accesscontrol;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtTokenService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final ObjectMapper objectMapper;
    private final byte[] key;
    private final long expireMinutes;

    public JwtTokenService(ObjectMapper objectMapper,
                           @Value("${platform.security.jwt-key:}") String encodedKey,
                           @Value("${platform.security.encryption-key:}") String encryptionKey,
                           @Value("${platform.security.jwt-expire-minutes:120}") long expireMinutes) {
        this.objectMapper = objectMapper;
        if (encodedKey == null || encodedKey.isBlank()) encodedKey = encryptionKey;
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("PLATFORM_JWT_KEY or PLATFORM_ENCRYPTION_KEY is required");
        }
        try {
            this.key = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("PLATFORM_JWT_KEY must be Base64 encoded", exception);
        }
        if (key.length < 32) throw new IllegalStateException("PLATFORM_JWT_KEY must contain at least 256 bits");
        this.expireMinutes = Math.max(5, expireMinutes);
    }

    public TokenResult issue(UserPrincipal principal) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(expireMinutes, ChronoUnit.MINUTES);
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", principal.username());
        claims.put("uid", principal.id());
        claims.put("name", principal.displayName());
        claims.put("role", principal.role());
        // 令牌版本与用户记录绑定；修改角色、密码、停用或退出时递增版本即可使旧令牌失效。
        claims.put("ver", principal.tokenVersion());
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());
        String header = encode(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encode(claims);
        String content = header + "." + payload;
        return new TokenResult(content + "." + ENCODER.encodeToString(sign(content)), expiresAt);
    }

    public JwtClaims verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) throw invalidToken();
            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] supplied = DECODER.decode(parts[2]);
            // 常量时间比较，避免通过签名比较耗时推测正确签名的前缀。
            if (!MessageDigest.isEqual(expected, supplied)) throw invalidToken();
            Map<String, Object> header = objectMapper.readValue(DECODER.decode(parts[0]), new TypeReference<>() {});
            if (!"HS256".equals(header.get("alg")) || !"JWT".equals(header.get("typ"))) throw invalidToken();
            Map<String, Object> claims = objectMapper.readValue(DECODER.decode(parts[1]), new TypeReference<>() {});
            long expiresAt = number(claims.get("exp"));
            if (Instant.now().getEpochSecond() >= expiresAt) {
                throw new BusinessException(HttpStatus.UNAUTHORIZED, "IP-AUTH-003", "登录状态已过期");
            }
            long issuedAt = number(claims.get("iat"));
            long now = Instant.now().getEpochSecond();
            if (issuedAt > now + 60 || issuedAt >= expiresAt || number(claims.get("uid")) <= 0
                    || number(claims.get("ver")) < 1 || string(claims.get("jti")).length() > 100) {
                throw invalidToken();
            }
            return new JwtClaims(number(claims.get("uid")), string(claims.get("sub")),
                    string(claims.get("name")), string(claims.get("role")), number(claims.get("ver")), expiresAt);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidToken();
        }
    }

    private String encode(Object value) {
        try {
            return ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JacksonException exception) {
            throw new IllegalStateException("JWT serialization failed", exception);
        }
    }

    private byte[] sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("JWT signing failed", exception);
        }
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        throw invalidToken();
    }

    private String string(Object value) {
        if (value instanceof String text && !text.isBlank()) return text;
        throw invalidToken();
    }

    private BusinessException invalidToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "IP-AUTH-002", "登录凭证无效");
    }

    public record TokenResult(String accessToken, Instant expiresAt) {}
    public record JwtClaims(long userId, String username, String displayName, String role,
                            long tokenVersion, long expiresAtEpochSecond) {}
}
