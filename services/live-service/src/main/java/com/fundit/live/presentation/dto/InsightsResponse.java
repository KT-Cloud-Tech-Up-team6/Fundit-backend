package com.fundit.live.presentation.dto;

import com.fundit.live.application.question.QuestionInsightService.Insights;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code aiStatus}가 필요한 이유: 요구사항정의서 6.4.4.4가 "AI 준비 미완료"와
 * "집계된 질문 없음"을 다른 문구로 안내하라고 한다. 빈 배열만으로는 구분할 수 없다.
 *
 * <p>정렬은 <b>질문 발생 건수 내림차순</b> 단일 기준이다(6.4.4.3).
 */
public record InsightsResponse(String aiStatus, Map<String, Integer> topics,
                               List<Question> representativeQuestions) {

    public record Question(UUID questionId, String summaryText, int count, boolean answered) {
    }

    public static InsightsResponse from(Insights i) {
        return new InsightsResponse(i.aiStatus(), i.topics(),
                i.representativeQuestions().stream()
                        .map(q -> new Question(q.getPublicId(), q.getSummaryText(),
                                q.getRelatedQuestionCount(), q.isAnswered()))
                        .toList());
    }
}
