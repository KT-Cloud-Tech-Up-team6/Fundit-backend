package com.fundit.project.application.port;

import java.util.List;
import java.util.UUID;

/** 알림 발송은 notification-service 소관이다. 발송 실패가 본래 작업을 되돌리게 하지 않는다. */
public interface NotificationPort {

    void notifyProjectOpened(Long projectId, List<UUID> memberIds);

    void notifyProjectRejected(Long projectId, UUID sellerId, String rejectReason);

    void notifyNoticePublished(Long projectId, Long noticeId, List<UUID> followerIds);

    void notifyQuestionAnswered(Long postId, UUID askerId);
}
