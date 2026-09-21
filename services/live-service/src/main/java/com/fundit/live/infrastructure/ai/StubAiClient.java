package com.fundit.live.infrastructure.ai;

import com.fundit.live.application.ai.AiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 서버 주소도 계약도 확정 전이라 쓰는 스텁. {@code live.ai.mode=stub}일 때만 뜬다 — 기본값으로 두면 운영에서 스텁이 조용히 선택돼 큐시트·추천답변이 {@code "[stub] ..."} 문자열로 "성공"한다.
 *
 * <p>비동기 요청(큐시트·하이라이트)은 아무것도 하지 않는다 — 결과는 AI가 내부 엔드포인트로
 * 밀어주는 구조라 스텁이 흉내 낼 대상이 없다. Q&A/FAQ는 반대로 전부 동기 호출이라
 * 빈 값·PREPARING 상태로 흉내 낸다.
 */
@Component
@ConditionalOnProperty(name = "live.ai.mode", havingValue = "stub", matchIfMissing = false)
public class StubAiClient implements AiClient {

    @Override
    public void requestCueSheet(String liveId, CueSheetRequest request) {
        // 요청만 거는 경로다. 스텁은 성공으로 두고, 결과 수신은 내부 엔드포인트가 담당한다.
    }

    @Override
    public void requestHighlights(String liveId, String vodUrl, java.util.UUID highlightId) {
        // 위와 같다.
    }

    @Override
    public void prepare(String liveId, PrepareRequest request) {
        // 색인할 AI가 없다 — 아무것도 하지 않는다.
    }

    @Override
    public void updateContext(String liveId, ContextUpdate update) {
        // 위와 같다.
    }

    @Override
    public CommentBatchResult submitComments(String liveId, List<CommentInput> comments) {
        // 전부 무시 처리로 흉내 낸다 — 답변이 있는 척하면 화면 검증이 어긋난다.
        List<IgnoredComment> ignored = comments.stream()
                .map(c -> new IgnoredComment(c.commentId(), "STUB"))
                .toList();
        return new CommentBatchResult(List.of(), ignored, List.of());
    }

    @Override
    public FaqResult faq(String liveId, int topN) {
        return new FaqResult(180, List.of());
    }

    @Override
    public FaqComments faqComments(String liveId, String qid) {
        return new FaqComments(qid, 0, List.of());
    }

    @Override
    public UnansweredList unanswered(String liveId, int topN) {
        return new UnansweredList(List.of(), List.of());
    }

    @Override
    public UnansweredDetail unansweredDetail(String liveId, String qid) {
        return new UnansweredDetail("", 0, new Reference(List.of(), List.of()), null, null);
    }

    @Override
    public SellerAnswerResult registerSellerAnswer(String liveId, String qid, String answerText) {
        return new SellerAnswerResult(false);
    }

    @Override
    public SummaryResult summary(String liveId, int topN) {
        return new SummaryResult(0, 0, List.of(), Map.of());
    }
}
