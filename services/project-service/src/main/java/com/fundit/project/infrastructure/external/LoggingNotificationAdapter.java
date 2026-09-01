package com.fundit.project.infrastructure.external;

import com.fundit.project.application.port.NotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** notification-service 미연동 상태의 임시 어댑터. 발송 대상만 로그로 남긴다. */
@Slf4j
@Component
public class LoggingNotificationAdapter implements NotificationPort {

    @Override
    public void notifyProjectOpened(Long projectId, List<UUID> memberIds) {
        log.info("프로젝트 오픈 알림 대상 {}명. projectId={}", memberIds.size(), projectId);
    }

    @Override
    public void notifyProjectRejected(Long projectId, UUID sellerId, String rejectReason) {
        log.info("프로젝트 반려 알림. projectId={}, sellerId={}", projectId, sellerId);
    }

    @Override
    public void notifyNoticePublished(Long projectId, Long noticeId, List<UUID> followerIds) {
        log.info("새소식 알림 대상 {}명. projectId={}, noticeId={}", followerIds.size(), projectId, noticeId);
    }

    @Override
    public void notifyQuestionAnswered(Long postId, UUID askerId) {
        log.info("답변 등록 알림. postId={}, askerId={}", postId, askerId);
    }
}
