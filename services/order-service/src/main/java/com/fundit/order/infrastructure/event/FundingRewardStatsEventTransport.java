package com.fundit.order.infrastructure.event;

import com.fundit.order.application.funding.FundingRewardStatsPublisher.RewardStatsUpdatedEvent;

/** 아웃박스에 적재된 리워드 구매 통계를 실제 채널로 보내는 전송 포트. */
public interface FundingRewardStatsEventTransport {

    /** outboxId는 소비 측 멱등의 근거가 되는 eventId("order:{outboxId}")의 재료다(event-convention.md 5번). */
    void send(RewardStatsUpdatedEvent event, Long outboxId);
}
