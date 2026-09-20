package com.fundit.auth.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class BlindIndexUnitTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final BlindIndex blindIndex = new BlindIndex(KEY);

    @Test
    void 같은_평문은_항상_같은_해시가_된다() {
        // given & when & then — 이게 성립해야 암호화된 컬럼을 조회할 수 있다
        assertThat(blindIndex.of(BlindIndex.LABEL_EMAIL, "user@fundit.com"))
                .isEqualTo(blindIndex.of(BlindIndex.LABEL_EMAIL, "user@fundit.com"));
    }

    @Test
    void 이메일은_대소문자를_구분하지_않는다() {
        // given & when & then — 가입 때와 조회 때 표기가 갈리면 계정을 못 찾는다
        assertThat(blindIndex.of(BlindIndex.LABEL_EMAIL, "User@Fundit.com"))
                .isEqualTo(blindIndex.of(BlindIndex.LABEL_EMAIL, "user@fundit.com"));
    }

    @Test
    void 전화번호는_숫자만_남겨_비교한다() {
        // given & when & then — 010-1234-5678과 01012345678은 같은 번호다
        assertThat(blindIndex.of(BlindIndex.LABEL_PHONE, "010-1234-5678"))
                .isEqualTo(blindIndex.of(BlindIndex.LABEL_PHONE, "01012345678"));
    }

    @Test
    void 같은_값이라도_용도가_다르면_해시가_다르다() {
        // given & when & then — 한 컬럼의 해시를 다른 컬럼 조회에 갖다 쓸 수 없어야 한다
        assertThat(blindIndex.of(BlindIndex.LABEL_NAME, "01012345678"))
                .isNotEqualTo(blindIndex.of(BlindIndex.LABEL_PHONE, "01012345678"));
    }

    @Test
    void null은_그대로_null이다() {
        // given & when & then — 소셜 전용 계정은 전화번호 해시가 없다
        assertThat(blindIndex.of(BlindIndex.LABEL_PHONE, null)).isNull();
    }
}
