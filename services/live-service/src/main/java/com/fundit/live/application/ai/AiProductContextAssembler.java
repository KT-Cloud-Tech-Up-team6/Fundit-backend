package com.fundit.live.application.ai;

import com.fundit.live.application.project.ProjectContextClient;
import com.fundit.live.application.project.ProjectRewardClient;
import com.fundit.live.domain.session.LiveSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    /** 프로젝트가 없으면(404) 상품 필드는 null, knowledge는 빈 리스트로 보낸다. */
    public AiClient.PrepareRequest assemble(LiveSession session) {
        ProjectContextClient.ProjectContext context = projectContextClient
                .find(session.getProjectId()).orElse(null);
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
}
