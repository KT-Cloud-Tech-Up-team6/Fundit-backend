package com.fundit.live.infrastructure.ai;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ai.AiClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 서버(`/api/v1/ai`) 실연동. {@code live.ai.mode=http}일 때만 뜬다.
 *
 * <p>큐시트·하이라이트는 지금도 계약 미정이라 이 구현체가 손대지 않는다 — 그 두 메서드는
 * 여전히 {@code StubAiClient}가 맡거나(스텁 모드), 별도 계약이 확정되면 그때 채운다.
 * <b>이 클래스에서 그 두 메서드를 호출하면 안 된다</b> — 미구현으로 두고
 * {@code UnsupportedOperationException}을 던져 잘못 배선됐을 때 조용히 무시되지 않게 한다.
 *
 * <p>응답은 신뢰하지 않고 구조 확인 후 사용한다(security.md S7) — 다만 필드가 record라서
 * 필수값이 없으면 역직렬화 시점에 이미 걸러진다. 여기서는 상태코드만 본다.
 */
@Component
@ConditionalOnProperty(name = "live.ai.mode", havingValue = "http")
public class HttpAiClient implements AiClient {

    private final RestClient restClient;
    private final RestClient commentsRestClient;

    public HttpAiClient(@Qualifier("aiRestClient") RestClient restClient,
                        @Qualifier("aiCommentsRestClient") RestClient commentsRestClient) {
        this.restClient = restClient;
        this.commentsRestClient = commentsRestClient;
    }

    @Override
    public void requestCueSheet(String liveId, CueSheetRequest request) {
        throw new UnsupportedOperationException("큐시트 계약 미정 — HttpAiClient가 다룰 대상이 아니다.");
    }

    @Override
    public void requestHighlights(String liveId, String vodUrl, UUID highlightId) {
        throw new UnsupportedOperationException("하이라이트 계약 미정 — HttpAiClient가 다룰 대상이 아니다.");
    }

    @Override
    public void prepare(String liveId, PrepareRequest request) {
        call(() -> restClient.post()
                .uri("/lives/{liveId}/prepare", liveId)
                .body(request)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void updateContext(String liveId, ContextUpdate update) {
        call(() -> restClient.put()
                .uri("/lives/{liveId}/context", liveId)
                .body(update)
                .retrieve()
                .toBodilessEntity());
    }

    /**
     * {@code commentsRestClient}로 부른다 — 배치 50건이면 최대 1분까지 걸릴 수 있어
     * 다른 호출과 같은 타임아웃을 쓰면 안 된다({@link AiClientConfig} 참고).
     *
     * <p>409는 {@code NOT_PREPARED}다 — {@code prepare}를 안 부르고 채팅을 보낸 경우라
     * 우리 쪽 배선 실수다. 사용자에게 보일 값이 아니라 그대로 예외로 올려 로그에 남긴다.
     */
    @Override
    public CommentBatchResult submitComments(String liveId, List<CommentInput> comments) {
        try {
            return commentsRestClient.post()
                    .uri("/lives/{liveId}/comments", liveId)
                    .body(new CommentsRequest(comments))
                    .retrieve()
                    .onStatus(status -> status.value() == 409,
                            (req, res) -> {
                                throw new BusinessException(CommonErrorCode.CONFLICT,
                                        "AI 상품정보 색인이 먼저 필요합니다(prepare 미호출).");
                            })
                    .body(CommentBatchResult.class);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    @Override
    public FaqResult faq(String liveId, int topN) {
        return call(() -> restClient.get()
                .uri("/lives/{liveId}/faq?top_n={topN}", liveId, topN)
                .retrieve()
                .body(FaqResult.class));
    }

    @Override
    public FaqComments faqComments(String liveId, String qid) {
        return call(() -> restClient.get()
                .uri("/lives/{liveId}/faq/{qid}/comments", liveId, qid)
                .retrieve()
                .body(FaqComments.class));
    }

    @Override
    public UnansweredList unanswered(String liveId, int topN) {
        return call(() -> restClient.get()
                .uri("/lives/{liveId}/unanswered?top_n={topN}", liveId, topN)
                .retrieve()
                .body(UnansweredList.class));
    }

    @Override
    public UnansweredDetail unansweredDetail(String liveId, String qid) {
        try {
            return restClient.get()
                    .uri("/lives/{liveId}/unanswered/{qid}", liveId, qid)
                    .retrieve()
                    // 존재하지 않는 qid는 404로 온다 — 그대로 "없는 질문"으로 흘린다.
                    .onStatus(status -> status.value() == 404,
                            (req, res) -> {
                                throw new BusinessException(CommonErrorCode.NOT_FOUND);
                            })
                    .body(UnansweredDetail.class);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    @Override
    public SellerAnswerResult registerSellerAnswer(String liveId, String qid, String answerText) {
        try {
            return restClient.post()
                    .uri("/lives/{liveId}/unanswered/{qid}/answer", liveId, qid)
                    .body(Map.of("answer_text", answerText))
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (req, res) -> {
                                throw new BusinessException(CommonErrorCode.NOT_FOUND);
                            })
                    .body(SellerAnswerResult.class);
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    @Override
    public SummaryResult summary(String liveId, int topN) {
        return call(() -> restClient.get()
                .uri("/lives/{liveId}/summary?top_n={topN}", liveId, topN)
                .retrieve()
                .body(SummaryResult.class));
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    /** {@code prepare} 요청 바디는 필드가 평평한데(POST body 자체가 PrepareRequest), 댓글만 감싼다. */
    private record CommentsRequest(List<CommentInput> comments) {
    }
}
