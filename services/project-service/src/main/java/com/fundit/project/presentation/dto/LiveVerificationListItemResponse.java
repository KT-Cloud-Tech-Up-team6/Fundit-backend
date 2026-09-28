package com.fundit.project.presentation.dto;

import com.fundit.project.application.liveverification.LiveQuestionAnswerView;

/** {@code questionText}는 live-service 질문요약이 아직 수신되지 않은 과거 항목에서만 null이다. */
public record LiveVerificationListItemResponse(Long liveVerificationId, String questionSummaryId,
                                               String questionText, int questionCount, String answer) {

    public static LiveVerificationListItemResponse from(LiveQuestionAnswerView view) {
        return new LiveVerificationListItemResponse(view.liveVerificationId(), view.questionSummaryId(),
                view.questionText(), view.questionCount(), view.answer());
    }
}
