package com.fundit.order.infrastructure.event;

import com.fundit.order.application.funding.FundingEventPublisher.FundingCancelledByMemberEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingGoalFailedEvent;
import com.fundit.order.application.funding.FundingEventPublisher.FundingSucceededEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * 아웃박스에 적재된 펀딩 이벤트를 실제 채널로 보내는 전송 포트.
 * 브로커(RabbitMQ/Kafka)가 확정되면 이 인터페이스의 구현체만 교체한다.
 */
public interface FundingEventTransport {

    /** outboxId는 소비 측 멱등의 근거가 되는 eventId("order:{outboxId}")의 재료다(event-convention.md 5번). */
    void sendGoalFailed(FundingGoalFailedEvent event, Long outboxId);

    /**
     * sellerId/achievedAt은 payment-service {@code FundingSucceededListener}가 기대하는 필드 추가분이다
     * (event-convention.md 6번 "적용 예"). {@code FundingSucceededEvent} 자체(도메인 발행 의도)는
     * fundingId/projectId만 유지하고, 이 wire 전용 필드는 배선 계층(워커)에서만 채운다 —
     * FundingGoalJudgmentService의 판정 트랜잭션에 project-service 동기 호출을 끌어들이지 않기 위함.
     */
    void sendSucceeded(FundingSucceededEvent event, UUID sellerId, Instant achievedAt, Long outboxId);

    void sendCancelledByMember(FundingCancelledByMemberEvent event, Long outboxId);
}
