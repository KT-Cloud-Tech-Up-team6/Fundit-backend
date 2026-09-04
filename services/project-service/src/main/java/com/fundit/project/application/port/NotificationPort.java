package com.fundit.project.application.port;

import java.util.List;
import java.util.UUID;

/**
 * 알림 발송은 notification-service 소관이다.
 * 호출은 본래 작업 트랜잭션 커밋 이후({@code AFTER_COMMIT})에만 하고,
 * 구현체가 {@code DependencyFailureException}을 던지더라도 본래 작업을 되돌리지 않아야 한다.
 */
public interface NotificationPort {

    void notifyProjectOpened(Long projectId, List<UUID> memberIds);

    void notifyProjectRejected(Long projectId, UUID sellerId, String rejectReason);

    void notifyNoticePublished(Long projectId, Long noticeId, List<UUID> followerIds);

    void notifyQuestionAnswered(Long postId, UUID askerId);
}
