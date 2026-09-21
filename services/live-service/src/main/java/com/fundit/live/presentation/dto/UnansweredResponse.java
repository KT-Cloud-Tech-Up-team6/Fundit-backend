package com.fundit.live.presentation.dto;

import com.fundit.live.application.question.QuestionInsightService;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;

import java.util.List;
import java.util.UUID;

/** 미답변 질문 창(요구사항정의서 6.4.4.5). {@code pending}은 답변 대기, {@code answered}는 완료(회색 처리). */
public record UnansweredResponse(List<Item> pending, List<Item> answered) {

    public record Item(UUID questionId, String representativeText, int count) {
        public static Item from(LiveQuestionSummaryJpaEntity e) {
            return new Item(e.getPublicId(), e.getSummaryText(), e.getRelatedQuestionCount());
        }
    }

    public static UnansweredResponse from(QuestionInsightService.UnansweredView view) {
        return new UnansweredResponse(
                view.pending().stream().map(Item::from).toList(),
                view.answered().stream().map(Item::from).toList());
    }
}
