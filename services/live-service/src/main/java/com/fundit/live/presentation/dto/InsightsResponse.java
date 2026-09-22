package com.fundit.live.presentation.dto;

import com.fundit.live.application.question.QuestionInsightService;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 집계된 Q&A(요구사항정의서 6.4.4.2). 정렬은 AI가 이미 확정해 내려준 순서를 그대로 쓴다 —
 * BE가 다시 정렬하지 않는다(집계 로직이 AI로 이관됐다).
 *
 * <p>{@code aiStatus}가 {@code PREPARING}이면 상품정보 색인이 아직 안 끝난 것이고,
 * {@code READY}인데 {@code qna}가 비면 모인 질문이 없는 것이다. 요구사항정의서 6.4.4.4가
 * 두 상태를 <b>다른 문구</b>로 안내하라고 요구해서 나뉘어 있다.
 */
public record InsightsResponse(String aiStatus, List<Item> qna) {

    public record Item(UUID questionId, String summaryText, int count, String category,
                       String answeredBy, Instant answeredAt, String answerText, boolean promoted) {

        public static Item from(LiveQuestionSummaryJpaEntity e) {
            return new Item(e.getPublicId(), e.getSummaryText(), e.getRelatedQuestionCount(),
                    e.getTopic(), e.getAnsweredBy() == null ? "NONE" : e.getAnsweredBy().name(),
                    e.getAnsweredAt(), e.getAnswerText(), e.isPromoted());
        }
    }

    public static InsightsResponse from(QuestionInsightService.InsightsView view) {
        return new InsightsResponse(view.aiPreparedAt() == null ? "PREPARING" : "READY",
                view.summaries().stream().map(Item::from).toList());
    }
}
