package com.lzcer.interfaceplatform.accesscontrol;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import com.lzcer.interfaceplatform.common.security.CredentialCipher;
import com.lzcer.interfaceplatform.gateway.GatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalApiAuthServiceTests {

    private static final String ENCRYPTION_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String APP_KEY = "ak_test";
    private static final String APP_SECRET = "sk_test_secret";

    private final HashSet<String> usedNonces = new HashSet<>();
    private ExternalApiAuthService authService;

    @BeforeEach
    void setUp() {
        usedNonces.clear();
        ApiNonceMapper nonceMapper = new ApiNonceMapper() {
            @Override public int deleteExpired() { return 0; }
            @Override public int insert(long clientId, String nonce, Timestamp expiresAt) {
                if (!usedNonces.add(clientId + ":" + nonce)) throw new DataIntegrityViolationException("duplicate nonce");
                return 1;
            }
            @Override public int deleteByClientId(long clientId) { return 0; }
        };
        ApiClientService clientService = new ApiClientService(null, new CredentialCipher(ENCRYPTION_KEY), nonceMapper) {
            @Override
            public AuthenticatedClient findEnabledByAppKey(String appKey) {
                return APP_KEY.equals(appKey)
                        ? new AuthenticatedClient(9L, "WMS", "WMS", APP_KEY, APP_SECRET) : null;
            }
        };
        authService = new ExternalApiAuthService(clientService, nonceMapper, 300);
    }

    @Test
    void usesDocumentedCanonicalStringAndPreventsNonceReplay() {
        String timestamp = Long.toString(Instant.now().toEpochMilli());
        String nonce = "nonce-1234567890";
        GatewayService.GatewayRequest request = request(timestamp, nonce, "placeholder");
        String canonical = authService.canonical(request, timestamp, nonce);
        GatewayService.GatewayRequest signedRequest = request(timestamp, nonce, hmac(canonical));

        ExternalApiAuthService.Caller caller = authService.authenticate(signedRequest);

        assertThat(caller.code()).isEqualTo("WMS");
        assertThat(canonical).isEqualTo("POST\n/open-api/material/query\nwarehouse=WH01\n" + timestamp
                + "\n" + nonce + "\n9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
        assertThat(usedNonces).hasSize(1);
        assertThatThrownBy(() -> authService.authenticate(signedRequest))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IP-SIGN-007"));
    }

    @Test
    void rejectsInvalidSignatureBeforeItCanCreateANonce() {
        GatewayService.GatewayRequest request = request(Long.toString(Instant.now().toEpochMilli()),
                "nonce-2345678901", "not-a-valid-signature");

        assertThatThrownBy(() -> authService.authenticate(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IP-SIGN-006"));
        assertThat(usedNonces).isEmpty();
    }

    @Test
    void rejectsRequestsOutsideTheConfiguredTimeWindow() {
        GatewayService.GatewayRequest request = request("0", "nonce-3456789012", "0".repeat(64));

        assertThatThrownBy(() -> authService.authenticate(request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IP-SIGN-004"));
    }

    @Test
    void preservesTheRawQueryInTheCanonicalString() {
        String timestamp = Long.toString(Instant.now().toEpochMilli());
        GatewayService.GatewayRequest request = new GatewayService.GatewayRequest("GET", "/open-api/material/query",
                "b=two&a=space%20value&repeat=2&repeat=1", Map.of(), Map.of(), new byte[0], "127.0.0.1");

        assertThat(authService.canonical(request, timestamp, "nonce-4567890123"))
                .startsWith("GET\n/open-api/material/query\nb=two&a=space%20value&repeat=2&repeat=1\n" + timestamp);
    }

    private GatewayService.GatewayRequest request(String timestamp, String nonce, String signature) {
        return new GatewayService.GatewayRequest("POST", "/open-api/material/query", "warehouse=WH01",
                Map.of("warehouse", List.of("WH01")), Map.of(
                "X-App-Key", List.of(APP_KEY),
                "X-Timestamp", List.of(timestamp),
                "X-Nonce", List.of(nonce),
                "X-Signature", List.of(signature)), "test".getBytes(StandardCharsets.UTF_8), "127.0.0.1");
    }

    private String hmac(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
