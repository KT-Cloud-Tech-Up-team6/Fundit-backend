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
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 서버(`/api/v1/ai`) 실연동. {@code live.ai.mode=http}일 때만 뜬다.
 *
 * <p>큐시트는 Q&A 코파일럿과 <b>다른 AI 서버</b>다(별도 base-url·토큰,
 * {@code cuesheetRestClient} — {@link AiClientConfig} 참고). 하이라이트는 Q&A와 같은 서버를 쓰되
 * 타임아웃만 분리했다({@code highlightsRestClient}) — 계약이 2026-09-23 확정됐다.
 *
 * <p>응답은 신뢰하지 않고 구조 확인 후 사용한다(security.md S7). 댓글 배치 응답에서 빠진
 * 컬렉션은 {@link AiClient.CommentBatchResult}가 빈 리스트로 바꾼다.
 */
@Component
@ConditionalOnProperty(name = "live.ai.mode", havingValue = "http")
public class HttpAiClient implements AiClient {

    private final RestClient restClient;
    private final RestClient commentsRestClient;
    private final RestClient cuesheetRestClient;
    private final RestClient highlightsRestClient;

    public HttpAiClient(@Qualifier("aiRestClient") RestClient restClient,
                        @Qualifier("aiCommentsRestClient") RestClient commentsRestClient,
                        @Qualifier("cuesheetAiRestClient") RestClient cuesheetRestClient,
                        @Qualifier("aiHighlightsRestClient") RestClient highlightsRestClient) {
        this.restClient = restClient;
        this.commentsRestClient = commentsRestClient;
        this.cuesheetRestClient = cuesheetRestClient;
        this.highlightsRestClient = highlightsRestClient;
    }

    /**
     * 동기 호출이다 — {@code cuesheetRestClient}의 읽기 타임아웃이 200초라 이 메서드를
     * 요청 스레드에서 그대로 부르면 안 된다({@code CueSheetService}가 별도 스레드에서 부른다).
     *
     * <p>실패는 두 갈래다: HTTP 오류는 {@code call()}이 이미 {@code DependencyFailureException}으로
     * 감싸고, 200인데 본문이 {@code status=FAILED}거나 구간이 비어 있으면 여기서 같은 예외로
     * 통일한다 — 호출부가 실패 경로 하나만 처리하면 되게.
     */
    @Override
    public String requestCueSheet(String liveId, CueSheetRequest request) {
        CueSheetGenerationResponse response = call(() -> cuesheetRestClient.post()
                .uri("/cue-sheets")
                .body(CueSheetHttpRequest.of(liveId, request))
                .retrieve()
                .body(CueSheetGenerationResponse.class));
        if (response == null) {
            throw new DependencyFailureException(new IllegalStateException("AI가 빈 응답을 보냈습니다."));
        }
        if ("FAILED".equals(response.status())) {
            throw new DependencyFailureException(new IllegalStateException(
                    response.failureReason() == null ? "AI가 큐시트 생성에 실패했습니다." : response.failureReason()));
        }
        if (response.segments() == null || !response.segments().isArray() || response.segments().isEmpty()) {
            throw new DependencyFailureException(new IllegalStateException("AI가 구간을 비워 보냈습니다."));
        }
        return response.segments().toString();
    }

    /**
     * {@code liveId}를 감싸지 않고 나머지 필드와 나란히(평평하게) 보낸다 — 합의된 바디 모양이
     * {@code {"live_id": ..., "mode": ..., "product": {...}, ...}}라 {@code CueSheetRequest}를
     * {@code "request"} 키로 감싸면 안 된다.
     */
    private record CueSheetHttpRequest(String liveId, String mode, int targetDurationSec, boolean demoAvailable,
                                       List<String> emphasisPoints, String tone, List<String> mandatoryPhrases,
                                       PrepareRequest product, FundingInfo funding) {
        static CueSheetHttpRequest of(String liveId, CueSheetRequest r) {
            return new CueSheetHttpRequest(liveId, r.mode(), r.targetDurationSec(), r.demoAvailable(),
                    r.emphasisPoints(), r.tone(), r.mandatoryPhrases(), r.product(), r.funding());
        }
    }

    /** {@code status}는 실패일 때만 온다(성공 예시는 {@code {"segments":[...]}} 뿐이라 null 허용). */
    private record CueSheetGenerationResponse(String status, JsonNode segments, String failureReason) {
    }

    /**
     * 결과는 콜백(push)으로 오므로 여기선 202 접수 응답만 확인한다 — 큐시트처럼 결과 자체를
     * 기다리지 않는다({@code highlightsRestClient} 타임아웃이 10초로 짧은 이유).
     */
    @Override
    public void requestHighlights(String liveId, String vodUrl, UUID highlightId,
                                  List<CommentInput> chats, String productName) {
        call(() -> highlightsRestClient.post()
                .uri("/lives/{liveId}/highlights", liveId)
                .body(new HighlightsHttpRequest(vodUrl, highlightId, chats, productName))
                .retrieve()
                .toBodilessEntity());
    }

    private record HighlightsHttpRequest(String vodUrl, UUID highlightId, List<CommentInput> chats,
                                         String productName) {
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
                                // 외부 의존성 실패로 올린다 — BusinessException이면 발송기의 catch를
                                // 빠져나가 같은 주기의 다른 LIVE 세션 발송까지 끊긴다.
                                throw new DependencyFailureException(CommonErrorCode.CONFLICT,
                                        new IllegalStateException("AI 상품정보 색인이 먼저 필요합니다(prepare 미호출)."));
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
