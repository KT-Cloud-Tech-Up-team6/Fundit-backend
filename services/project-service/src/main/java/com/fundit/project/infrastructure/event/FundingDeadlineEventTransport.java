package com.fundit.project.infrastructure.event;

import com.fundit.project.application.project.FundingDeadlinePublisher.FundingDeadlineReachedEvent;

/**
 * 아웃박스에 적재된 펀딩 마감 도래 이벤트를 실제 채널로 보내는 전송 포트.
 * 브로커가 확정되면 이 인터페이스의 구현체만 교체한다.
 */
public interface FundingDeadlineEventTransport {

    /** outboxId는 소비 측 멱등의 근거가 되는 eventId("project:{outboxId}")의 재료다(event-convention.md 5번). */
    void send(FundingDeadlineReachedEvent event, Long outboxId);
}
