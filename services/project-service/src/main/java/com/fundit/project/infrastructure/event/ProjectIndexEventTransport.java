package com.fundit.project.infrastructure.event;

import com.fundit.project.application.project.ProjectIndexEventPublisher.ProjectIndexedEvent;

/**
 * 아웃박스에 적재된 프로젝트 색인 이벤트를 실제 채널로 보내는 전송 포트
 * (RewardEventTransport와 동일 패턴).
 */
public interface ProjectIndexEventTransport {

    /** outboxId는 소비 측 멱등의 근거가 되는 eventId("project:{outboxId}")의 재료다(event-convention.md 5번). */
    void sendApproved(ProjectIndexedEvent event, Long outboxId);

    void sendUpdated(ProjectIndexedEvent event, Long outboxId);
}
