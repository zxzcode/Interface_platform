package com.lzcer.interfaceplatform.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialCipherTests {

    private final CredentialCipher cipher = new CredentialCipher(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    @Test
    void encryptsWithRandomIvAndDecryptsOriginalValue() {
        String first = cipher.encrypt("readonly-password");
        String second = cipher.encrypt("readonly-password");

        assertThat(first).isNotEqualTo(second).doesNotContain("readonly-password");
        assertThat(cipher.decrypt(first)).isEqualTo("readonly-password");
        assertThat(cipher.decrypt(second)).isEqualTo("readonly-password");
    }
}
