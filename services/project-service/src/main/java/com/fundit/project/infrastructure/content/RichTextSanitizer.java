package com.fundit.project.infrastructure.content;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 프로젝트 스토리 TEXT 블록의 굵게/색상/정렬 서식을 HTML로 보존하면서 XSS를 막는다(security.md S2,
 * "게시글·댓글·자유입력 텍스트가 포함된 응답 필드는 특히 주의"). 허용 태그는 Jsoup {@link Safelist}로
 * 제한하고, {@code style} 속성은 한 번 더 검증한다 — Safelist가 속성 자체는 통과시켜도 그 안의
 * CSS 선언까지 검사하지는 않아서, style 속성을 통째로 허용하면 예전 IE의
 * {@code expression()}/{@code url(javascript:...)} 같은 CSS 인젝션 경로가 그대로 열린다.
 * 그래서 {@link #ALLOWED_DECLARATIONS}에 맞는 선언만 남기고 나머지는 버린다({@code url()}은 어느 규칙에도 맞지 않는다).
 */
@Component
public class RichTextSanitizer {

    private static final Safelist SAFELIST = Safelist.none()
            .addTags("b", "strong", "i", "em", "u", "p", "br", "span", "div", "ul", "ol", "li",
                    "section", "h2", "h3", "hr")
            .addAttributes("span", "style")
            .addAttributes("p", "style")
            .addAttributes("div", "style")
            .addAttributes("section", "style")
            .addAttributes("h2", "style")
            .addAttributes("h3", "style")
            .addAttributes("hr", "style");

    private static final String PX = "\\d{1,3}px";
    private static final String LINE = "\\d{1,2}px\\s+solid\\s+#[0-9a-fA-F]{3,6}";

    /** 서식별 허용 선언. 제목·소제목·구분선 값은 AI Funding Story 결과(#153)에 맞췄다. */
    private static final List<Pattern> ALLOWED_DECLARATIONS = List.of(
            Pattern.compile("^color:\\s*#[0-9a-fA-F]{3,6}$"),
            Pattern.compile("^text-align:\\s*(left|center|right|justify)$"),
            Pattern.compile("^font-weight:\\s*(bold|normal|[1-9]00)$"),
            Pattern.compile("^font-size:\\s*" + PX + "$"),
            Pattern.compile("^line-height:\\s*(\\d(\\.\\d{1,2})?|" + PX + ")$"),
            Pattern.compile("^border:\\s*(0|" + LINE + ")$"),
            Pattern.compile("^border-(top|left):\\s*" + LINE + "$"),
            Pattern.compile("^padding-left:\\s*" + PX + "$"),
            Pattern.compile("^margin:\\s*(0|" + PX + ")(\\s+(0|" + PX + ")){0,3}$"));

    public String sanitize(String html) {
        if (html == null) {
            return null;
        }
        String cleaned = Jsoup.clean(html, SAFELIST);
        Document doc = Jsoup.parseBodyFragment(cleaned);
        for (Element el : doc.body().getAllElements()) {
            String style = el.attr("style");
            if (style.isBlank()) {
                continue;
            }
            String filtered = filterStyle(style);
            if (filtered.isBlank()) {
                el.removeAttr("style");
            } else {
                el.attr("style", filtered);
            }
        }
        return doc.body().html();
    }

    private String filterStyle(String style) {
        StringBuilder kept = new StringBuilder();
        for (String declaration : style.split(";")) {
            String trimmed = declaration.trim();
            if (ALLOWED_DECLARATIONS.stream().anyMatch(p -> p.matcher(trimmed).matches())) {
                kept.append(trimmed).append("; ");
            }
        }
        return kept.toString().trim();
    }

    /**
     * 태그를 걷어낸 뒤 보이는 글자가 남는지. 에디터가 보내는 {@code <p></p>}·{@code <p>&nbsp;</p>}는
     * 문자열로는 비어 있지 않아 {@code @NotBlank}를 통과한다(#212). {@code isBlank()}로는 부족하다 —
     * 그 기준인 {@link Character#isWhitespace}가 줄바꿈 없는 공백류(U+00A0 {@code &nbsp;},
     * U+2007 {@code &numsp;}, U+202F {@code &nnbsp;})를 공백으로 보지 않아서, 그 문자만 든 문단이
     * 본문으로 통과한다. 그래서 두 판정을 모두 쓴다 — 여백류는 {@link Character#isSpaceChar} 쪽에 걸린다.
     */
    public boolean hasVisibleText(String html) {
        return html != null && Jsoup.parse(html).text().codePoints()
                .anyMatch(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp));
    }

    /**
     * "실질적으로 빈 본문" 판정(#212) — 이미지/영상 블록이 하나도 없고 보이는 글자도 없는 상태.
     * 블록을 버리지는 않는다. 임시저장에서 에디터의 빈 줄이 조용히 사라지면 그게 다음 버그다.
     */
    public boolean isEmptyStory(List<IntroContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return true;
        }
        return blocks.stream().noneMatch(b -> b.type() != IntroContentType.TEXT || hasVisibleText(b.value()));
    }
}
