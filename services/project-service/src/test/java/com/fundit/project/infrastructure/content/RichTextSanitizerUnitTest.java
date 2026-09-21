package com.fundit.project.infrastructure.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RichTextSanitizerUnitTest {

    private final RichTextSanitizer sanitizer = new RichTextSanitizer();

    @Test
    void 허용된_서식_태그는_그대로_유지된다() {
        // given
        String html = "<p><b>굵게</b> <i>기울임</i> <u>밑줄</u></p>";

        // when
        String result = sanitizer.sanitize(html);

        // then
        assertThat(result).isEqualTo(html);
    }

    @Test
    void 색상_텍스트정렬_굵기_스타일은_유지된다() {
        // given
        String html = "<p style=\"text-align: center\"><span style=\"color: #ff0000; font-weight: bold\">빨강</span></p>";

        // when
        String result = sanitizer.sanitize(html);

        // then
        assertThat(result).contains("text-align: center").contains("color: #ff0000").contains("font-weight: bold");
    }

    @Test
    void script_태그는_제거된다() {
        // given
        String html = "<script>alert('xss')</script><b>굵게</b>";

        // when
        String result = sanitizer.sanitize(html);

        // then
        assertThat(result).doesNotContain("script").isEqualTo("<b>굵게</b>");
    }

    @Test
    void 허용되지_않은_CSS_선언은_제거된다() {
        // given — background-image:url(javascript:...) 같은 CSS 인젝션 경로 차단
        String html = "<span style=\"color: #123456; background-image: url(javascript:alert(1))\">텍스트</span>";

        // when
        String result = sanitizer.sanitize(html);

        // then
        assertThat(result).contains("color: #123456").doesNotContain("background-image").doesNotContain("javascript:");
    }

    @Test
    void onclick같은_이벤트_속성은_제거된다() {
        // given
        String html = "<p onclick=\"alert(1)\">텍스트</p>";

        // when
        String result = sanitizer.sanitize(html);

        // then
        assertThat(result).doesNotContain("onclick");
    }

    @Test
    void null_입력은_null을_반환한다() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }
}
