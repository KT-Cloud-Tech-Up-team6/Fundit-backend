package com.fundit.payment.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCipherUnitTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void 같은_평문이라도_암호문은_매번_다르고_복호화하면_원문이_된다() {
        // given
        AesGcmCipher cipher = new AesGcmCipher(KEY);

        // when
        String first = cipher.encrypt("계좌번호-123");
        String second = cipher.encrypt("계좌번호-123");

        // then
        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("계좌번호-123");
        assertThat(cipher.decrypt(second)).isEqualTo("계좌번호-123");
    }

    @Test
    void 잘못된_암호문은_복호화에_실패한다() {
        AesGcmCipher cipher = new AesGcmCipher(KEY);

        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString("not-a-cipher".getBytes())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("복호화");
    }
}

