package com.fundit.project.infrastructure.content;

import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void 빈_배열은_빈_본문이다() {
        assertThat(sanitizer.isEmptyStory(List.of())).isTrue();
        assertThat(sanitizer.isEmptyStory(null)).isTrue();
    }

    @Test
    void 보이는_글자가_없는_TEXT_블록만_있으면_빈_본문이다() {
        // given — 에디터가 실제로 보내는 형태(#212)
        for (String html : List.of("<p></p>", "<p>&nbsp;</p>", "<p> </p>", "<p><br></p>", "")) {
            List<IntroContentBlock> blocks = List.of(new IntroContentBlock(IntroContentType.TEXT, html));

            // when & then
            assertThat(sanitizer.isEmptyStory(blocks)).as(html).isTrue();
        }
    }

    @Test
    void 글자가_한_자라도_있으면_빈_본문이_아니다() {
        // given
        List<IntroContentBlock> blocks = List.of(
                new IntroContentBlock(IntroContentType.TEXT, "<p>&nbsp;</p>"),
                new IntroContentBlock(IntroContentType.TEXT, "<p><b>가</b></p>"));

        // when & then
        assertThat(sanitizer.isEmptyStory(blocks)).isFalse();
    }

    @Test
    void 이미지나_영상_블록만_있어도_빈_본문이_아니다() {
        // given
        List<IntroContentBlock> onlyImage = List.of(
                new IntroContentBlock(IntroContentType.TEXT, "<p></p>"),
                new IntroContentBlock(IntroContentType.IMAGE, "https://cdn/media/projects/1/a.jpg"));
        List<IntroContentBlock> onlyVideo = List.of(
                new IntroContentBlock(IntroContentType.VIDEO_URL, "https://youtube.com/watch?v=1"));

        // when & then
        assertThat(sanitizer.isEmptyStory(onlyImage)).isFalse();
        assertThat(sanitizer.isEmptyStory(onlyVideo)).isFalse();
    }

    @Test
    void 줄바꿈_없는_공백류만_든_문단도_빈_본문이다() {
        // given — isBlank()의 기준인 Character.isWhitespace가 이 문자들을 공백으로 보지 않는다
        for (String space : List.of("\u00A0", "\u2007", "\u202F")) {
            List<IntroContentBlock> blocks = List.of(
                    new IntroContentBlock(IntroContentType.TEXT, "<p>" + space + "</p>"));

            // when & then
            assertThat(sanitizer.isEmptyStory(blocks))
                    .as("U+%04X", (int) space.charAt(0)).isTrue();
        }
    }

    @Test
    void 공백류_사이에_글자가_있으면_빈_본문이_아니다() {
        // given
        List<IntroContentBlock> blocks = List.of(
                new IntroContentBlock(IntroContentType.TEXT, "<p>\u00A0\u2007\uAC00\u202F</p>"));

        // when & then
        assertThat(sanitizer.isEmptyStory(blocks)).isFalse();
    }
}
