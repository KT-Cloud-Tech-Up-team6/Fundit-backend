package com.fundit.project.application.project;

import com.fundit.project.application.port.NotificationPort;
import com.fundit.project.application.project.event.ProjectOpenedEvent;
import com.fundit.project.application.project.event.ProjectRejectedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 검수 트랜잭션이 커밋된 뒤에만 알림을 보낸다.
 * 알림 실패({@code DependencyFailureException} 포함)가 승인/반려 상태를 되돌리지 않게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectReviewNotificationListener {

    private final NotificationPort notificationPort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectOpened(ProjectOpenedEvent event) {
        try {
            notificationPort.notifyProjectOpened(event.projectId(), event.memberIds());
        } catch (RuntimeException ex) {
            log.warn("프로젝트 오픈 알림 실패. projectId={}", event.projectId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectRejected(ProjectRejectedEvent event) {
        try {
            notificationPort.notifyProjectRejected(event.projectId(), event.sellerId(), event.rejectReason());
        } catch (RuntimeException ex) {
            log.warn("프로젝트 반려 알림 실패. projectId={}", event.projectId(), ex);
        }
    }
}
