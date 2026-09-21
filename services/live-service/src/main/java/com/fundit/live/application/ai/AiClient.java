package com.fundit.live.application.ai;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * AI 추론 서버(`/api/v1/ai`) 연동 포트.
 *
 * <p><b>큐시트·하이라이트와 Q&A/FAQ는 호출 방향이 다르다.</b> 큐시트·하이라이트는 요청만 걸고
 * 결과는 AI가 내부 엔드포인트로 밀어준다(push). Q&A/FAQ는 그럴 비동기 결과가 없다 —
 * BE가 채팅을 배치로 넘기면 그 응답으로 바로 답변이 온다({@code submitComments}), 그리고
 * BE가 필요할 때마다 집계 결과를 조회한다(AI팀 실계약 v1, 2026-09-17 E2E 검증 완료).
 * FAQ 클러스터링(유사질문 병합·3분 윈도우 승격)은 <b>AI가 전부 한다</b> — live-service는
 * 두 번 집계하지 않고 응답을 그대로 저장·노출한다.
 *
 * <p><b>질문 식별자가 두 종류다.</b> {@code submitComments} 응답의 {@code questionId}는
 * 댓글 1건이 분류된 결과(예: {@code q_0001})이고, FAQ 계열({@code faq}/{@code unanswered}/
 * {@code registerSellerAnswer})의 {@code qid}는 <b>집계된 클러스터</b> ID(예: {@code fq_0002})다
 * — 서로 다른 값이라 섞어 쓰면 안 된다. 원본 댓글이 필요하면 클러스터 쪽은
 * {@link #faqComments}로 따로 받는다.
 */
public interface AiClient {

    /** 큐시트 생성 요청(요구사항정의서 6.2.4.2). 반환 없이 요청만 건다. */
    void requestCueSheet(String liveId, CueSheetRequest request);

    /**
     * 하이라이트 자동 생성 요청(요구사항정의서 6.6.4). 방송 종료 후 호출된다.
     *
     * <p>{@code highlightId}는 <b>재생성 대상</b>이며 최초 생성은 {@code null}이다. AI가 결과를
     * 밀어줄 때 이 값을 되돌려줘야 기존 행을 갱신한다 — 안 그러면 재생성이 새 행을 만들어
     * 원래 행이 {@code GENERATING}으로 영영 남고 클립 수가 상한에 걸려 재생성 자체가 막힌다.
     */
    void requestHighlights(String liveId, String vodUrl, java.util.UUID highlightId);

    /**
     * 상품정보 색인. LIVE 시작 시 1회 필수 — 안 하면 {@link #submitComments}가 409(`NOT_PREPARED`)를
     * 낸다. 상품이 바뀌면(재방송 등) 이 API만 재호출한다 — 재학습·재배포가 필요 없다.
     */
    void prepare(String liveId, PrepareRequest request);

    /** 방송·펀딩 실시간 값. 답변 내 동적 슬롯(마감일·달성률·잔여수량)에 즉시 반영된다. */
    void updateContext(String liveId, ContextUpdate update);

    /**
     * 채팅 배치 분석 + 답변(최대 50건/배치, 3초 주기 권장). <b>동기</b>다 — 배치 크기에 따라
     * 최대 1분까지 걸릴 수 있어(댓글당 평균 1초 순차 처리) 다른 엔드포인트보다 긴 타임아웃이
     * 필요하다.
     */
    CommentBatchResult submitComments(String liveId, List<CommentInput> comments);

    /** 집계된 Q&A(판매자 화면·시청자 Q&A 버튼 공용). */
    FaqResult faq(String liveId, int topN);

    /** 집계 건수를 클릭했을 때 그 클러스터의 원본 댓글 전체. */
    FaqComments faqComments(String liveId, String qid);

    /** 미답변 질문 창(질문 요약). */
    UnansweredList unanswered(String liveId, int topN);

    /** 미답변 질문 클릭 — 참고정보 + 답변 초안. */
    UnansweredDetail unansweredDetail(String liveId, String qid);

    /**
     * 판매자 답변 등록. 등록되면 이후 같은/유사 질문은 LLM 없이 이 답변으로 즉시 응답되고
     * (`SELLER_CONFIRMED`), Live Knowledge에 등록돼 상품 지식으로 축적된다.
     */
    SellerAnswerResult registerSellerAnswer(String liveId, String qid, String answerText);

    /** 방송 종료 후 요약. */
    SummaryResult summary(String liveId, int topN);

    /** {@code product}는 {@code prepare}와 같은 모양이다 — 큐시트 담당과 합의해 파서를 공유한다. */
    record CueSheetRequest(String mode, int targetDurationSec, boolean demoAvailable,
                           List<String> emphasisPoints, String tone, List<String> mandatoryPhrases,
                           PrepareRequest product) {
    }

    /**
     * {@code grounded=false}는 상품정보에서 근거를 찾지 못했다는 뜻이며 <b>에러가 아니다</b> —
     * 503으로 올리면 화면에 보여줄 게 없어지는데 요구사항정의서 6.4.4.5는 Empty State를 요구한다.
     */
    record AnswerDraft(String draftAnswer, boolean grounded) {
    }

    // ── 상품정보 색인 ──────────────────────────────────────────────

    record PrepareRequest(String productName, String productCategory, String categoryMinor,
                          String projectDisplayCode, String projectPublicId,
                          List<KnowledgeChunk> knowledge, List<RewardInfo> rewards) {
    }

    /** {@code prepare} 입력 전용 — 원문 근거(source)까지 싣는다. 응답 쪽 참고 청크는 {@link ReferenceChunk}. */
    record KnowledgeChunk(String chunkId, String category, String text, boolean strict, String source) {
    }

    record RewardInfo(String rewardDisplayCode, String name, String description, long price,
                      boolean isLimited, Integer quantity, boolean isEarlyBird,
                      List<OptionGroup> optionGroups) {
    }

    record OptionGroup(String name, List<String> values) {
    }

    // ── 실시간 컨텍스트 ────────────────────────────────────────────

    record ContextUpdate(String projectId, BroadcastInfo broadcast, FundingInfo funding,
                         Map<String, Object> extraSlots) {
    }

    record BroadcastInfo(Instant endAt, boolean vodEnabled) {
    }

    record FundingInfo(Instant deadline, int achievedRate) {
    }

    // ── 댓글 배치(A-3) ─────────────────────────────────────────────

    record CommentInput(String commentId, String text, long atMs, java.util.UUID senderId) {
    }

    /** AI가 빈 컬렉션 필드를 생략해도 {@code null}이 새지 않게 빈 리스트로 바꾼다. */
    record CommentBatchResult(List<AnsweredQuestion> questions, List<IgnoredComment> ignored,
                              List<CommentError> errors) {
        public CommentBatchResult {
            questions = questions == null ? List.of() : questions;
            ignored = ignored == null ? List.of() : ignored;
            errors = errors == null ? List.of() : errors;
        }
    }

    /**
     * {@code questionId}는 이 댓글이 분류된 결과 ID다(예: {@code q_0001}) — FAQ 클러스터
     * {@code qid}와는 다른 값이다. {@code answer}가 {@code null}이면
     * {@link HandledBy#UNANSWERABLE}이고, 그때만 {@code topics}가 채워진다.
     */
    record AnsweredQuestion(String questionId, String commentId, String text, HandledBy handledBy,
                            String category, long atMs, GeneratedAnswer answer, List<TopicTag> topics) {
    }

    /**
     * {@code strict=true}면 문구 가공이 금지된다(줄임·꾸밈 불가) — 약관·정확 수치이므로
     * 원문 그대로 노출해야 한다.
     */
    record GeneratedAnswer(String text, Grounding grounding, boolean strict, String source) {
    }

    enum HandledBy {PRODUCT, PLATFORM, UNANSWERABLE}

    enum Grounding {GROUNDED, PARTIAL_GROUNDED, SELLER_CONFIRMED}

    /** UNANSWERABLE 댓글의 관심 유형(기능 B-7 랭킹 재료). */
    record TopicTag(String category, String topic) {
    }

    record IgnoredComment(String commentId, String reason) {
    }

    /** 개별 댓글의 LLM 실패. 임의 답변으로 대체하지 않고 재시도 대상으로 남긴다. */
    record CommentError(String commentId, String code, String message) {
    }

    // ── FAQ(B-1·B-2) ───────────────────────────────────────────────

    record FaqResult(int windowSec, List<FaqItem> qna) {
    }

    /**
     * {@code qid}는 집계된 클러스터 ID다({@code fq_0002} 형태). {@code answeredByLabel}은
     * AI가 만든 한글 라벨인데 쓰지 않는다 — 표시 문구를 우리가 {@code answeredBy}에서
     * 직접 만드는 쪽이 다른 화면 문구와 일관된다.
     */
    record FaqItem(String qid, String representativeText, int count, String category,
                   AnsweredBy answeredBy, Instant answeredAt, String answer, boolean promoted) {
    }

    enum AnsweredBy {SELLER, AI, NONE}

    /** 집계 건수 클릭 → 원본 댓글 전체(B-2). */
    record FaqComments(String qid, int count, List<FaqComment> comments) {
    }

    record FaqComment(String commentId, String text, long atMs) {
    }

    // ── 미답변(B-3·B-4·B-5) ────────────────────────────────────────

    record UnansweredList(List<UnansweredItem> pending, List<UnansweredItem> answered) {
    }

    record UnansweredItem(String qid, String representativeText, int count) {
    }

    record UnansweredDetail(String question, int count, Reference reference, String draft,
                            String sellerAnswer) {
    }

    /** 판매자 참고용 — 근거가 아니라 참고 정보다. {@link KnowledgeChunk}와 달리 원문 출처가 없다. */
    record Reference(List<ReferenceChunk> chunks, List<String> images) {
    }

    record ReferenceChunk(String chunkId, String category, String text) {
    }

    record SellerAnswerResult(boolean liveKnowledgeRegistered) {
    }

    // ── 요약(B-6) ────────────────────────────────────────────────

    record SummaryResult(int totalQuestions, int uniqueQuestions, List<String> topQuestions,
                         Map<String, List<String>> byCategory) {
    }
}
