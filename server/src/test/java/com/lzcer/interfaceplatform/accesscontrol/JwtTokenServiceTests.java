package com.lzcer.interfaceplatform.accesscontrol;

import com.lzcer.interfaceplatform.common.api.BusinessException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTests {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private final JwtTokenService tokenService = new JwtTokenService(new ObjectMapper(), KEY, "", 5);

    @Test
    void issuesAndVerifiesSignedJwtWithUserTokenVersion() {
        JwtTokenService.TokenResult token = tokenService.issue(new UserPrincipal(42L, "operator",
                "Operator", "OPERATOR", 7L));

        JwtTokenService.JwtClaims claims = tokenService.verify(token.accessToken());

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.username()).isEqualTo("operator");
        assertThat(claims.role()).isEqualTo("OPERATOR");
        assertThat(claims.tokenVersion()).isEqualTo(7L);
        assertThat(claims.expiresAtEpochSecond()).isGreaterThan(System.currentTimeMillis() / 1000);
    }

    @Test
    void rejectsJwtWhenSignatureWasTamperedWith() {
        String token = tokenService.issue(new UserPrincipal(1L, "admin", "Admin", "ADMIN", 1L)).accessToken();
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertThatThrownBy(() -> tokenService.verify(tampered))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IP-AUTH-002"));
    }

    @Test
    void rejectsAnExpiredButOtherwiseCorrectlySignedJwt() throws Exception {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"admin\",\"uid\":1,\"name\":\"Admin\",\"role\":\"ADMIN\",\"ver\":1,\"iat\":1,\"exp\":1}");
        String content = header + "." + payload;
        String expiredToken = content + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(content));

        assertThatThrownBy(() -> tokenService.verify(expiredToken))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("IP-AUTH-003"));
    }

    private byte[] hmac(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(KEY), "HmacSHA256"));
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
