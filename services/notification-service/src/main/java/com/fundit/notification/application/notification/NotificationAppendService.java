package com.fundit.notification.application.notification;

import com.fundit.notification.infrastructure.persistence.notification.NotificationJpaRepository;
import com.fundit.notification.infrastructure.persistence.notificationsetting.NotificationSettingJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NOTI-006 적재. 수신설정을 확인하고 알림 행을 만든다.
 *
 * <p>중복 판정 로직을 따로 짜지 않는다 — (event_id, member_id) UNIQUE 제약에
 * ON CONFLICT DO NOTHING으로 맡기는 게 짧고 정확하다.
 *
 * <p><b>[천장]</b> 온사이트 전용인 동안만 이 구조로 충분하다. 지금은 이벤트 하나의 사이드이펙트가
 * notifications INSERT 하나뿐이라 UNIQUE로 끝난다. 이메일·웹푸시가 붙으면 사이드이펙트가 둘이 되어
 * "INSERT 성공 → 발송 전 장애 → 재수신 시 UNIQUE로 스킵 → 발송 영영 유실"이 생긴다.
 * 그때는 발송 상태 컬럼(sent_at 등) + 재시도 워커가 필요하다.
 */
@Service
@RequiredArgsConstructor
public class NotificationAppendService implements NotificationEventListener {

    private final NotificationJpaRepository notificationJpaRepository;
    private final NotificationSettingJpaRepository notificationSettingJpaRepository;

    @Override
    @Transactional
    public void onNotificationRaised(NotificationRaisedEvent event) {
        // 행이 존재하면 수신 거부다 — 생성하지 않고 정상 처리한다(실패가 아니다).
        if (notificationSettingJpaRepository.existsByMemberIdAndNotifType(event.memberId(), event.notifType())) {
            return;
        }
        notificationJpaRepository.insertIgnoringConflict(
                event.eventId(), event.memberId(), event.notifType().name(), event.title(), event.relatedUrl());
    }
}
