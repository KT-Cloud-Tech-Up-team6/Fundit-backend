package com.fundit.project.presentation.dto;

import com.fundit.project.application.liveverification.LiveQuestionAnswerView;

/** 판매자 답변 등록 화면용 — 미답변 질문은 {@code answered=false}, {@code liveVerificationId=null}로 내려간다. */
public record LiveQuestionListItemResponse(String questionSummaryId, String questionText, int questionCount,
                                           boolean answered, Long liveVerificationId, String answer) {

    public static LiveQuestionListItemResponse from(LiveQuestionAnswerView view) {
        return new LiveQuestionListItemResponse(view.questionSummaryId(), view.questionText(), view.questionCount(),
                view.answered(), view.liveVerificationId(), view.answer());
    }
}
