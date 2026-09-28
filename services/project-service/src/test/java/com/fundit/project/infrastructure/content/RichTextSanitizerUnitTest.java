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
    void AI_Funding_Story_제목_소제목_구분선_서식은_그대로_보존된다() {
        // given — #153 AI 결과 샘플 값
        String html = "<section>"
                + "<h2 style=\"border-left:3px solid #202124; padding-left:10px; margin:0 0 24px; font-size:18px; line-height:1.45\">제목</h2>"
                + "<h3 style=\"margin:0 0 16px; font-size:16px; line-height:1.5\">소제목</h3>"
                + "<hr style=\"border:0; border-top:1px solid #e6e6e6; margin:36px 0\">"
                + "</section>";

        // when
        String result = sanitizer.sanitize(html);

        // then
        assertThat(result)
                .contains("<section>", "<h2", "<h3", "<hr")
                .contains("border-left:3px solid #202124; padding-left:10px; margin:0 0 24px; font-size:18px; line-height:1.45")
                .contains("margin:0 0 16px; font-size:16px; line-height:1.5")
                .contains("border:0; border-top:1px solid #e6e6e6; margin:36px 0");
        assertThat(sanitizer.sanitize(result)).isEqualTo(result);
    }

    @Test
    void 새로_연_태그에서도_허용_밖_CSS와_url은_제거된다() {
        // given
        String html = "<h2 style=\"position: fixed; border-left: 3px solid url(javascript:alert(1)); margin: -10px\" onclick=\"x()\">t</h2>";

        // when
        String result = sanitizer.sanitize(html);

        // then
        assertThat(result).isEqualTo("<h2>t</h2>");
    }

    @Test
    void null_입력은_null을_반환한다() {
        assertThat(sanitizer.sanitize(null)).isNull();
    }
}
