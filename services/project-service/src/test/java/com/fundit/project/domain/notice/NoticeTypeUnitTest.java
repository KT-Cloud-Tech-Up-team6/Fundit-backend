package com.fundit.project.domain.notice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NoticeType")
class NoticeTypeUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {"리워드안내", "이벤트", "제작과정", "발송정보", "달성률", "교환환불", "결제안내", "FAQ"})
    void 사전_정의된_유형이면_허용된다(String label) {
        // given & when & then
        assertThat(NoticeType.isSupported(label)).isTrue();
    }

    @Test
    void 정의되지_않은_유형이면_거부된다() {
        // given & when & then
        assertThat(NoticeType.isSupported("없는유형")).isFalse();
    }

    @Test
    void enum_이름으로는_허용되지_않는다() {
        // given & when & then — API는 한글 라벨로만 주고받는다
        assertThat(NoticeType.isSupported("SHIPPING")).isFalse();
    }
}
