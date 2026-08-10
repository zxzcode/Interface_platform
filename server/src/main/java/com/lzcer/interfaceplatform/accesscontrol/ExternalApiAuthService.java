package com.lzcer.interfaceplatform.accesscontrol;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.gateway.GatewayService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ExternalApiAuthService {

    private final ApiClientService clientService;
    private final JdbcClient jdbcClient;
    private final long allowedSkewSeconds;

    public ExternalApiAuthService(ApiClientService clientService, JdbcClient jdbcClient,
                                  @Value("${platform.security.signature-skew-seconds:300}") long allowedSkewSeconds) {
        this.clientService = clientService;
        this.jdbcClient = jdbcClient;
        this.allowedSkewSeconds = Math.min(3600, Math.max(30, allowedSkewSeconds));
    }

    @Transactional
    public Caller authenticate(GatewayService.GatewayRequest request) {
        String appKey = requiredHeader(request.headers(), "x-app-key");
        String timestampText = requiredHeader(request.headers(), "x-timestamp");
        String nonce = requiredHeader(request.headers(), "x-nonce");
        String signature = requiredHeader(request.headers(), "x-signature");
        if (!nonce.matches("[A-Za-z0-9_-]{16,64}")) throw unauthorized("IP-SIGN-002", "Nonce 格式无效");
        if (!signature.matches("[0-9a-f]{64}")) throw unauthorized("IP-SIGN-006", "请求签名无效");

        ApiClientService.AuthenticatedClient client = clientService.findEnabledByAppKey(appKey);
        if (client == null) throw unauthorized("IP-SIGN-005", "调用方凭证无效");

        long timestamp;
        try { timestamp = Long.parseLong(timestampText); }
        catch (NumberFormatException exception) { throw unauthorized("IP-SIGN-003", "时间戳格式无效"); }
        Instant now = Instant.now();
        long delta;
        try {
            delta = Math.abs(Math.subtractExact(now.toEpochMilli(), timestamp));
        } catch (ArithmeticException exception) {
            throw unauthorized("IP-SIGN-004", "请求时间戳已过期");
        }
        if (delta > allowedSkewSeconds * 1000) throw unauthorized("IP-SIGN-004", "请求时间戳已过期");

        // 先校验时间窗和签名，再写入 Nonce；无效请求不应消耗防重放存储。
        String canonical = canonical(request, timestampText, nonce);
        String expected = hmac(client.appSecret(), canonical);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII))) {
            throw unauthorized("IP-SIGN-006", "请求签名无效");
        }

        // 唯一约束由数据库兜底，并发请求携带同一 Nonce 时只能有一个请求成功。
        jdbcClient.sql("delete from ip_api_nonce where expires_at <= current_timestamp").update();
        try {
            jdbcClient.sql("""
                    insert into ip_api_nonce(client_id, nonce_value, expires_at)
                    values (:clientId, :nonce, :expiresAt)
                    """).param("clientId", client.id()).param("nonce", nonce)
                    .param("expiresAt", Timestamp.from(now.plusSeconds(allowedSkewSeconds * 2))).update();
        } catch (DataIntegrityViolationException exception) {
            throw unauthorized("IP-SIGN-007", "请求 Nonce 已使用，禁止重放");
        }
        return new Caller(client.id(), client.code(), client.name(), client.appKey());
    }

    public void authorize(Caller caller, String routeType, String resourceCode) {
        if (!clientService.isAuthorized(caller.id(), routeType, resourceCode)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "IP-SIGN-008", "调用方没有该接口权限");
        }
    }

    public String canonical(GatewayService.GatewayRequest request, String timestamp, String nonce) {
        return request.method().toUpperCase(Locale.ROOT) + "\n" + request.path() + "\n"
                + canonicalQuery(request.rawQuery()) + "\n" + timestamp + "\n" + nonce + "\n"
                + sha256(request.body());
    }

    private String canonicalQuery(String rawQuery) {
        // 签名约定使用请求原始查询串；不排序、不解码、不重新编码，调用方和平台才能计算出同一摘要。
        return rawQuery == null ? "" : rawQuery;
    }

    private String hmac(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC calculation failed", exception);
        }
    }

    private String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 calculation failed", exception);
        }
    }

    private String requiredHeader(Map<String, List<String>> headers, String name) {
        List<String> values = headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream()).filter(value -> value != null && !value.isBlank())
                .toList();
        if (values.size() != 1) throw unauthorized("IP-SIGN-001", "鉴权请求头必须且只能传递一次: " + name);
        return values.get(0);
    }

    private BusinessException unauthorized(String code, String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public record Caller(long id, String code, String name, String appKey) {}
}
