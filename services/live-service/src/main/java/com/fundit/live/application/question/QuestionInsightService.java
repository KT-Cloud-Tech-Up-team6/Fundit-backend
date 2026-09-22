package com.fundit.live.application.question;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI FAQ 조회(요구사항정의서 6.4.4.2·6.4.4.3).
 *
 * <p><b>집계는 AI가 한다.</b> 유사질문 병합·3분 윈도우 승격을 여기서 다시 하지 않는다 —
 * {@code GET /faq} 응답을 로컬에 그대로 반영(upsert)하고 그 결과를 내려준다. 로컬에 저장하는
 * 이유는 순전히 우리 쪽 화면 계약(질문마다 안정적인 {@code questionId})을 유지하기 위해서다.
 *
 * <p><b>이 API가 실패해도 방송·채팅은 정상이어야 한다</b>(6.4.4.2) — AI 준비 안 된 상태를
 * 에러가 아니라 상태값으로 알린다.
 */
@Service
@RequiredArgsConstructor
public class QuestionInsightService {

    private final LiveQuestionSummaryJpaRepository summaryRepository;
    private final LiveSessionRepository sessionRepository;
    private final AiClient aiClient;

    /**
     * 판매자 화면 — 집계된 Q&A(요구사항정의서 6.4.4.2).
     *
     * <p>{@code aiPreparedAt}을 같이 돌려주는 이유: 목록이 비었을 때 "AI 준비 중"과 "모인 질문 없음"을
     * 다른 문구로 안내해야 하는데(6.4.4.4), 세션은 여기서 이미 읽고 있어 추가 조회가 없다.
     */
    @Transactional
    public InsightsView faq(UUID sellerId, UUID liveId, int topN) {
        LiveSession session = loadOwned(sellerId, liveId);
        AiClient.FaqResult result = aiClient.faq(liveId.toString(), topN);
        List<LiveQuestionSummaryJpaEntity> summaries = result.qna().stream()
                .map(item -> upsert(session.getId(), item))
                .toList();
        return new InsightsView(summaries, session.getAiPreparedAt());
    }

    /** {@code aiPreparedAt}이 null이면 아직 상품정보 색인 전이다(PREPARING). */
    public record InsightsView(List<LiveQuestionSummaryJpaEntity> summaries, Instant aiPreparedAt) {
    }

    /** 대표질문(FAQ 클러스터) 원본 채팅(요구사항정의서 6.4.4.3). AI가 직접 갖고 있어 위임한다. */
    @Transactional(readOnly = true)
    public List<AiClient.FaqComment> originalMessages(UUID sellerId, UUID liveId, UUID questionId) {
        LiveSession session = loadOwned(sellerId, liveId);
        // 소속을 조회에 묶는다 — 남의 questionId로 원본 채팅을 읽을 수 없어야 한다(S4)
        LiveQuestionSummaryJpaEntity summary = loadSummaryOf(session, questionId);
        return aiClient.faqComments(liveId.toString(), summary.getAiQuestionId()).comments();
    }

    /** 미답변 질문 창(요구사항정의서 6.4.4.5). */
    @Transactional
    public UnansweredView unanswered(UUID sellerId, UUID liveId, int topN) {
        LiveSession session = loadOwned(sellerId, liveId);
        AiClient.UnansweredList result = aiClient.unanswered(liveId.toString(), topN);
        List<LiveQuestionSummaryJpaEntity> pending = result.pending().stream()
                .map(item -> upsertPending(session.getId(), item))
                .toList();
        // 답변 완료 행은 AiAnswerService가 기존 행에 recordAnswer로만 만든다 — 여기서 새로 만들면
        // 답변 정보 없는 "완료" 행이 생기므로 있는 행만 돌려준다.
        List<LiveQuestionSummaryJpaEntity> answered = result.answered().stream()
                .flatMap(item -> summaryRepository
                        .findBySessionIdAndAiQuestionId(session.getId(), item.qid()).stream())
                .toList();
        return new UnansweredView(pending, answered);
    }

    /** 미답변 질문 클릭 — 참고정보 + 답변 초안(요구사항정의서 6.4.4.5). */
    @Transactional(readOnly = true)
    public AiClient.UnansweredDetail unansweredDetail(UUID sellerId, UUID liveId, UUID questionId) {
        LiveSession session = loadOwned(sellerId, liveId);
        LiveQuestionSummaryJpaEntity summary = loadSummaryOf(session, questionId);
        return aiClient.unansweredDetail(liveId.toString(), summary.getAiQuestionId());
    }

    /** 소비자 Q&A 버튼 — 답변된 질문만, 질문 건수 내림차순(요구사항정의서 11.3.4). 인증 불필요. */
    @Transactional(readOnly = true)
    public List<LiveQuestionSummaryJpaEntity> answeredQuestions(UUID liveId) {
        LiveSession session = sessionRepository.findOwnedAny(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return summaryRepository.findBySessionIdAndAnsweredTrueOrderByRelatedQuestionCountDesc(
                session.getId());
    }

    /** FAQ 항목을 로컬 행으로 반영한다. 없으면 새로 만들고 있으면 갱신한다. */
    private LiveQuestionSummaryJpaEntity upsert(Long sessionId, AiClient.FaqItem item) {
        LiveQuestionSummaryJpaEntity summary = summaryRepository
                .findBySessionIdAndAiQuestionId(sessionId, item.qid())
                .orElseGet(() -> newSummary(sessionId, item.qid()));
        summary.applyFromAi(item);
        return summaryRepository.save(summary);
    }

    /** {@code UnansweredItem}은 {@code FaqItem}보다 필드가 적어(답변 전이라 당연하다) 최소 반영만 한다. */
    private LiveQuestionSummaryJpaEntity upsertPending(Long sessionId, AiClient.UnansweredItem item) {
        LiveQuestionSummaryJpaEntity summary = summaryRepository
                .findBySessionIdAndAiQuestionId(sessionId, item.qid())
                .orElseGet(() -> newSummary(sessionId, item.qid()));
        summary.applyFromAi(new AiClient.FaqItem(item.qid(), item.representativeText(), item.count(),
                summary.getTopic(), AiClient.AnsweredBy.NONE, null, null, summary.isPromoted()));
        return summaryRepository.save(summary);
    }

    private LiveQuestionSummaryJpaEntity newSummary(Long sessionId, String aiQuestionId) {
        return LiveQuestionSummaryJpaEntity.builder()
                .publicId(UUID.randomUUID())
                .sessionId(sessionId)
                .aiQuestionId(aiQuestionId)
                .summaryText("")
                .relatedQuestionCount(0)
                .answered(false)
                .build();
    }

    private LiveSession loadOwned(UUID sellerId, UUID liveId) {
        return sessionRepository.findOwned(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 요청한 LIVE에 속한 질문만 돌려준다 — 남의 questionId면 404다(S4·S10). */
    private LiveQuestionSummaryJpaEntity loadSummaryOf(LiveSession session, UUID questionId) {
        return summaryRepository.findByPublicIdAndSessionId(questionId, session.getId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    public record UnansweredView(List<LiveQuestionSummaryJpaEntity> pending,
                                 List<LiveQuestionSummaryJpaEntity> answered) {
    }
}
