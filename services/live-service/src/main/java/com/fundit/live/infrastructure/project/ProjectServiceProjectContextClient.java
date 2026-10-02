package com.fundit.live.infrastructure.project;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.project.ProjectContextClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * project-service 공개 상세 API({@code GET /api/v1/projects/{projectId}})에서 AI 컨텍스트에
 * 필요한 필드만 뽑아온다. {@code projectDisplayCode}는 이 응답에 없어(판매자용 목록 API 전용
 * 필드) AI 쪽엔 null로 보낸다(협의 확정 전까지 없는 값을 지어내지 않는다).
 *
 * <p>소개 본문은 여기서 <b>평문으로 바꿔서</b> 돌려준다({@link #toPlainText}) — HTML이
 * live-service 안으로 들어오는 유일한 지점이라 여기서 막으면 AI {@code prepare}와 큐시트
 * 두 경로가 같이 해결된다(#240).
 */
@Component
@RequiredArgsConstructor
public class ProjectServiceProjectContextClient implements ProjectContextClient {

    /**
     * 블록 태그. 평문으로 바꿀 때 공백이 되어야 하는 것들이다 —
     * {@code <p>A</p><p>B</p>}가 "AB"로 붙으면 문장 경계가 사라진다.
     */
    private static final Pattern BLOCK_TAG =
            Pattern.compile("(?i)</?(p|div|section|ul|ol|li|h2|h3|hr|br)\\b[^>]*>");

    /** 남은 인라인 태그({@code b/strong/i/em/u/span}). 지우기만 한다 — 공백을 넣으면 단어가 쪼개진다. */
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]*>");

    /** {@code &nbsp;}는 U+00A0으로 풀리는데 자바 {@code \s}에 걸리지 않아 따로 적는다. */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

    private final RestClient projectServiceRestClient;

    @Override
    public Optional<ProjectContext> find(UUID projectId) {
        try {
            ProjectDetail detail = projectServiceRestClient.get()
                    .uri("/api/v1/projects/{projectId}", projectId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> { })
                    .body(ProjectDetail.class);
            if (detail == null) {
                return Optional.empty();
            }
            List<String> introTexts = detail.introContent() == null ? List.of()
                    : detail.introContent().stream()
                            .filter(block -> "TEXT".equals(block.type()) && block.value() != null)
                            .map(block -> toPlainText(block.value()))
                            // 태그만 있던 블록(<p><br></p> 등)은 빈 문자열이 된다. 빈 knowledge
                            // 청크는 AI 근거에 쓸 내용이 없어 넘기지 않는다.
                            .filter(text -> !text.isEmpty())
                            .toList();
            Integer achievementRate = detail.fundingStatus() == null ? null : detail.fundingStatus().achievementRate();
            Integer remainingDays = detail.fundingStatus() == null ? null : detail.fundingStatus().remainingDays();
            Instant fundingDeadline = detail.fundingStatus() == null ? null : detail.fundingStatus().fundingDeadline();
            return Optional.of(new ProjectContext(detail.title(), detail.categoryMajor(), detail.categoryMinor(),
                    introTexts, achievementRate, remainingDays, fundingDeadline));
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    /**
     * 스토리 TEXT 블록의 HTML을 평문으로 만든다. project-service는 굵게/색상/정렬 서식을
     * HTML로 보존해서 내려준다(그쪽 {@code RichTextSanitizer}가
     * {@code b/strong/i/em/u/p/br/span/div/ul/ol/li/section/h2/h3/hr} + {@code style}만 남긴다).
     * 그대로 AI knowledge로 넘기면 근거와 답변 본문에 태그가 섞여 나온다.
     *
     * <p>엔티티 복원을 태그 제거 <b>뒤에</b> 하는 이유: 먼저 풀면 본문에 적힌
     * {@code &lt;b&gt;}가 진짜 태그가 되어 지워진다 — 글쓴이가 쓴 글자가 사라진다.
     *
     * <p>ponytail: 정규식으로 충분한 근거는 입력이 위 allowlist를 통과한 HTML이라는 것이다
     * (style 값도 {@code color}/{@code text-align}/{@code font-*} 꼴로 제한돼 {@code '>'}가 들어올 수 없다).
     * project-service가 allowlist를 넓혀 속성에 {@code '>'}가 들어올 수 있게 되면 jsoup으로 바꾼다.
     */
    private static String toPlainText(String html) {
        String spaced = BLOCK_TAG.matcher(html).replaceAll(" ");
        String stripped = ANY_TAG.matcher(spaced).replaceAll("");
        return WHITESPACE.matcher(HtmlUtils.htmlUnescape(stripped)).replaceAll(" ").strip();
    }

    private record ProjectDetail(String title, String categoryMajor, String categoryMinor,
                                 List<IntroBlock> introContent, FundingStatus fundingStatus) {
    }

    private record IntroBlock(String type, String value) {
    }

    private record FundingStatus(Integer achievementRate, Integer remainingDays, Instant fundingDeadline) {
    }
}
