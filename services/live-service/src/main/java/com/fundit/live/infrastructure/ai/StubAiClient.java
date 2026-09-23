package com.fundit.live.infrastructure.ai;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ai.AiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 서버 주소도 계약도 확정 전이라 쓰는 스텁. {@code live.ai.mode=stub}일 때만 뜬다 — 기본값으로 두면 운영에서 스텁이 조용히 선택돼 큐시트·추천답변이 {@code "[stub] ..."} 문자열로 "성공"한다.
 *
 * <p>하이라이트 요청은 아무것도 하지 않는다 — 결과는 AI가 내부 엔드포인트로 밀어주는 구조라
 * 스텁이 흉내 낼 대상이 없다(계약 미정). 큐시트는 <b>동기 호출로 바뀌어서</b>(2026-09-22 협의)
 * 캔 응답을 바로 돌려준다 — 안 그러면 콜백을 기다리던 옛 경로가 사라져 로컬에서 영원히
 * {@code GENERATING}에 머문다. Q&A/FAQ도 전부 동기라 빈 값·PREPARING 상태로 흉내 낸다.
 */
@Component
@ConditionalOnProperty(name = "live.ai.mode", havingValue = "stub", matchIfMissing = false)
public class StubAiClient implements AiClient {

    private static final String STUB_SEGMENTS = """
            [{"id":"stub-0","title":"오프닝","duration":30,"outline":"[stub] 상품 소개","script":"[stub] 안녕하세요."}]""";

    /**
     * QA/dev에서 큐시트 {@code FAILED} 상태를 재현하기 위한 매직 값(FE 요청) — PR #117로 내부
     * 콜백(임의 상태 주입 경로)이 삭제된 뒤로는 이 스텁이 항상 성공만 반환해 재현할 방법이
     * 없었다. {@code tone}에 이 값을 넣어 요청하면 실패를 흉내낸다. prod는 {@code live.ai.mode=http}라
     * 이 스텁 자체가 뜨지 않아 운영 코드 경로에는 영향이 없다.
     */
    static final String QA_FORCE_FAIL_TONE = "QA_FORCE_FAIL";

    @Override
    public String requestCueSheet(String liveId, CueSheetRequest request) {
        if (QA_FORCE_FAIL_TONE.equals(request.tone())) {
            throw new DependencyFailureException(new RuntimeException("QA_FORCE_FAIL"));
        }
        return STUB_SEGMENTS;
    }

    @Override
    public void requestHighlights(String liveId, String vodUrl, java.util.UUID highlightId,
                                  List<CommentInput> chats, String productName) {
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
