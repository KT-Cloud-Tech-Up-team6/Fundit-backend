package com.fundit.live.presentation.dto;

import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 집계된 Q&A(요구사항정의서 6.4.4.2). 정렬은 AI가 이미 확정해 내려준 순서를 그대로 쓴다 —
 * BE가 다시 정렬하지 않는다(집계 로직이 AI로 이관됐다).
 */
public record InsightsResponse(List<Item> qna) {

    public record Item(UUID questionId, String summaryText, int count, String category,
                       String answeredBy, Instant answeredAt, String answerText, boolean promoted) {

        public static Item from(LiveQuestionSummaryJpaEntity e) {
            return new Item(e.getPublicId(), e.getSummaryText(), e.getRelatedQuestionCount(),
                    e.getTopic(), e.getAnsweredBy() == null ? "NONE" : e.getAnsweredBy().name(),
                    e.getAnsweredAt(), e.getAnswerText(), e.isPromoted());
        }
    }

    public static InsightsResponse from(List<LiveQuestionSummaryJpaEntity> summaries) {
        return new InsightsResponse(summaries.stream().map(Item::from).toList());
    }
}
