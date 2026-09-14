package com.fundit.notification.application.notification;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import com.fundit.notification.infrastructure.persistence.notification.NotificationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** 알림함 조회·읽음 처리(NOTI-003/005/007). */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationJpaRepository notificationJpaRepository;

    /** NOTI-003. 본인 알림만 최신순으로 조회한다 — 회원 ID는 인증 컨텍스트에서만 온다(security.md S4). */
    @Transactional(readOnly = true)
    public Page<NotificationItem> getNotifications(UUID memberId, Pageable pageable) {
        return notificationJpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
                .map(n -> new NotificationItem(n.getId(), n.getNotifType(), n.getTitle(),
                        n.getRelatedUrl(), n.getReadAt(), n.getCreatedAt()));
    }

    /** NOTI-007. 목록(NOTI-003)은 페이징되므로 전체 안읽음 개수를 구할 수 없어 별도 경로로 센다. */
    @Transactional(readOnly = true)
    public long countUnread(UUID memberId) {
        return notificationJpaRepository.countByMemberIdAndReadAtIsNull(memberId);
    }

    /**
     * NOTI-005. 이미 읽은 알림에 다시 호출하면 기존 readAt을 그대로 반환한다(idempotent, 덮어쓰지 않음).
     * 존재하지 않거나 타인의 알림이면 403이 아니라 404다 — 403은 "그 알림이 존재한다"를 알려주므로
     * ID를 넣어보며 타인 알림의 존재 여부를 캐낼 수 있다(security.md S10).
     *
     * <p>동시 요청에서도 "최초 1회만 기록"이 지켜진다 — 조회에 PESSIMISTIC_WRITE가 걸려 있어
     * (NotificationJpaRepository#findByIdAndMemberId) 같은 알림에 대한 읽음 처리가 행 단위로 직렬화되고,
     * 뒤에 온 요청은 앞 트랜잭션이 커밋한 read_at을 읽어 markRead의 null 가드에서 그대로 통과한다.
     * 검증: NotificationServiceConcurrencyTest.
     */
    @Transactional
    public Instant markRead(Long notificationId, UUID memberId) {
        var notification = notificationJpaRepository.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다."));
        notification.markRead(Instant.now());
        return notification.getReadAt();
    }

    public record NotificationItem(Long notificationId, NotifType notifType, String title,
                                   String relatedUrl, Instant readAt, Instant createdAt) {
    }
}
