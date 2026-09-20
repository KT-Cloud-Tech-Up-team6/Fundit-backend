package com.fundit.member.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCipherUnitExceptionTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void 잘못된_암호문은_복호화에_실패한다() {
        // given
        AesGcmCipher cipher = new AesGcmCipher(KEY);
        String garbage = Base64.getEncoder().encodeToString("not-a-cipher".getBytes());

        // when & then
        assertThatThrownBy(() -> cipher.decrypt(garbage))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("복호화");
    }

    @Test
    void 키_길이가_32바이트가_아니면_기동을_실패시킨다() {
        // given — 그냥 두면 첫 암호화 호출 때야 터진다. 기동 시점에 잡는다
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        // when & then
        assertThatThrownBy(() -> new AesGcmCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }
}
