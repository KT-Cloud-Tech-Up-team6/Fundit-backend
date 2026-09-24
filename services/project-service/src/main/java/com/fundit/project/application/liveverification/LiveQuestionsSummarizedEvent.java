package com.fundit.project.application.liveverification;

import java.util.List;
import java.util.UUID;

/**
 * live-service {@code live.questions-summarized.v1} 수신 계약 — 발행 측 타입을 공유하지 않고
 * 소비 측에서 따로 선언한다(event-convention.md 4번). 모르는 필드(eventId 등)는 무시된다.
 *
 * <p>{@code projectId}는 project-service의 {@code public_id}(UUID)다 — 내부 BIGINT로의 변환은
 * 소비자 몫이다(live-service는 내부 PK를 알 방법이 없다).
 */
public record LiveQuestionsSummarizedEvent(String liveId, UUID projectId, List<Summary> summaries) {

    public record Summary(String questionSummaryId, String summaryText, Integer questionCount) {
    }
}
