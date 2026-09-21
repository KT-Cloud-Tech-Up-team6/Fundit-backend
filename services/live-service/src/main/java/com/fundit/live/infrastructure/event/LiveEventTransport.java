package com.fundit.live.infrastructure.event;

import java.util.List;

/**
 * 아웃박스 워커가 쓰는 발행 포트. 실패는 예외로 알려야 워커가 미발행으로 남긴다.
 *
 * <p>이벤트 레코드를 서비스 간에 공유하지 않는다 — 각자 선언하고 JSON이 계약이다
 * (event-convention.md 4번). 공유하면 생산자·소비자가 같이 배포돼야 하는 잠금이 생긴다.
 */
public interface LiveEventTransport {

    /** {@code live.ended.v1} */
    void sendLiveEnded(LiveEndedEvent event);

    /** {@code live.questions-summarized.v1} */
    void sendQuestionsSummarized(QuestionsSummarizedEvent event);

    /** {@code live.started.v1} — notification이 구독해 신청자에게 시작 알림을 만든다. */
    void sendLiveStarted(LiveStartedEvent event);

    record LiveEndedEvent(String eventId, String liveId, String projectId, String endedAt) {
    }

    /**
     * payload가 배열이라 아웃박스 컬럼이 JSONB다. {@code LiveDomainApiSpec.md}의
     * "질문요약 발행" 절과 필드명을 맞춘다 — {@code questionSummaryId}는
     * {@code live_question_summaries.public_id}다(project의 {@code question_summary_id}와 타입 일치).
     */
    record QuestionsSummarizedEvent(String eventId, String liveId, String projectId,
                                    List<SummaryItem> summaries) {

        public record SummaryItem(String questionSummaryId, String summaryText, int questionCount) {
        }
    }

    record LiveStartedEvent(String eventId, String liveId, String projectId, String startedAt) {
    }
}
