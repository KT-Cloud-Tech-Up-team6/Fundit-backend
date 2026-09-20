package com.fundit.auth.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlindIndexUnitExceptionTest {

    @Test
    void 키_길이가_32바이트가_아니면_기동을_실패시킨다() {
        // given
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        // when & then
        assertThatThrownBy(() -> new BlindIndex(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }
}
