package com.fundit.project.infrastructure.event;

import com.fundit.common.event.KafkaTopics;
import com.fundit.project.application.liveverification.LiveQuestionsSummarizedEvent;
import com.fundit.project.application.liveverification.LiveVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * PROJECT-019 — live-service가 방송 종료 후 발행하는 질문요약을 받아 LIVE검증 탭의 질문 목록을 채운다.
 * 이 이벤트가 유일한 질문 출처다(판매자는 답변만 등록한다). 비즈니스 로직은 두지 않는 얇은 어댑터
 * ({@link FundingRewardStatsKafkaListener}와 동일 패턴).
 */
@Component
@RequiredArgsConstructor
public class LiveQuestionsSummarizedKafkaListener {

    private final LiveVerificationService liveVerificationService;

    @KafkaListener(topics = KafkaTopics.LIVE_QUESTIONS_SUMMARIZED, groupId = "project-service")
    public void onQuestionsSummarized(LiveQuestionsSummarizedEvent event) {
        liveVerificationService.applySummaries(event.projectId(), event.summaries());
    }
}
