package com.fundit.live.presentation.dto;

import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * 소비자 Q&A 버튼(요구사항정의서 11.3.4).
 *
 * <p>{@code questionCount}는 필수다 — 시안이 질문 아래 "질문 12건"을 표시한다.
 * 같은 질문을 여러 명이 했다는 신호이고, 없으면 화면이 안 그려진다.
 */
public record AnsweredQuestionResponse(UUID questionId, String summaryText, int questionCount,
                                       String answerText, String answeredBy, Instant answeredAt) {

    public static AnsweredQuestionResponse from(LiveQuestionSummaryJpaEntity e) {
        return new AnsweredQuestionResponse(e.getPublicId(), e.getSummaryText(),
                e.getRelatedQuestionCount(), e.getAnswerText(), "SELLER", e.getAnsweredAt());
    }
}
