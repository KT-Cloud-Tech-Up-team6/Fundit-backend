package com.fundit.live.application.ai;

import com.fundit.live.application.project.ProjectContextClient;
import com.fundit.live.application.project.ProjectRewardClient;
import com.fundit.live.domain.session.LiveSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI에 보낼 상품정보(스토리·리워드)를 project-service에서 모아 한 모양으로 만든다.
 *
 * <p>Q&A {@code prepare}와 큐시트 생성 요청이 <b>같은 모양</b>을 쓴다(큐시트 담당과 합의) — 조립을
 * 한 곳에 두지 않으면 한쪽만 필드가 늘어 두 요청이 조용히 어긋난다.
 */
@Component
@RequiredArgsConstructor
public class AiProductContextAssembler {

    private final ProjectContextClient projectContextClient;
    private final ProjectRewardClient projectRewardClient;

    /**
     * 프로젝트를 못 찾으면(404) 빈 값을 채워 보내지 않고 <b>비워서 돌려준다.</b>
     * {@code product_category}는 AI 계약상 필수라 null로 보내면 422가 나고, 통과하더라도 빈 KB로
     * 색인돼 모든 상품 질문이 "확인이 어렵습니다"가 된다 — 색인을 안 한 상태로 두면 다음 채팅
     * 배치가 409(NOT_PREPARED)로 드러내 준다.
     */
    public Optional<AiClient.PrepareRequest> assemble(LiveSession session) {
        return projectContextClient.find(session.getProjectId()).map(context -> productOf(session, context));
    }

    /** 큐시트 요청용 — 상품정보와 캠페인 현황을 project-service 한 번 조회로 만든다. */
    public CueSheetInput forCueSheet(LiveSession session) {
        ProjectContextClient.ProjectContext context = projectContextClient.find(session.getProjectId()).orElse(null);
        return new CueSheetInput(productOf(session, context), fundingOf(context));
    }

    /**
     * 펀딩 실시간 값. 마감 시각은 project-service가 내려주는 원본을 그대로 쓴다 — "9월 15일
     * 23시 59분 마감"처럼 시:분까지 묻는 질문이 실제로 들어온다.
     *
     * <p>ponytail: {@code fundingDeadline}이 비면 남은 일수로 환산하는 fallback이 남아 있다.
     * project-service가 이 필드를 항상 채우는 게 확인되면 지운다 — 환산값은 하루 안쪽으로 어긋난다.
     */
    public static AiClient.FundingInfo fundingOf(ProjectContextClient.ProjectContext context) {
        int achievedRate = context == null || context.achievementRate() == null ? 0 : context.achievementRate();
        return new AiClient.FundingInfo(deadlineOf(context), achievedRate);
    }

    private static Instant deadlineOf(ProjectContextClient.ProjectContext context) {
        if (context == null) {
            return null;
        }
        if (context.fundingDeadline() != null) {
            return context.fundingDeadline();
        }
        return context.remainingDays() == null
                ? null : Instant.now().plusSeconds(context.remainingDays() * 86400L);
    }

    private AiClient.PrepareRequest productOf(LiveSession session, ProjectContextClient.ProjectContext context) {
        List<AiClient.RewardInfo> rewards = projectRewardClient.findRewards(session.getProjectId());
        List<AiClient.KnowledgeChunk> knowledge = context == null ? List.of() : knowledgeOf(context.introTexts());
        return new AiClient.PrepareRequest(
                context == null ? null : context.title(),
                context == null ? null : context.categoryMajor(),
                context == null ? null : context.categoryMinor(),
                null, session.getProjectId().toString(), knowledge, rewards);
    }

    private List<AiClient.KnowledgeChunk> knowledgeOf(List<String> introTexts) {
        List<AiClient.KnowledgeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < introTexts.size(); i++) {
            chunks.add(new AiClient.KnowledgeChunk("intro-" + i, "상세설명", introTexts.get(i), false, "project-intro"));
        }
        return chunks;
    }

    public record CueSheetInput(AiClient.PrepareRequest product, AiClient.FundingInfo funding) {
    }
}
