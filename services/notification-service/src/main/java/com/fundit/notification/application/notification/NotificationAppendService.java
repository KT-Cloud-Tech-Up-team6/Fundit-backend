package com.fundit.notification.application.notification;

import com.fundit.notification.infrastructure.persistence.livenotifyrequest.LiveNotifyRequestJpaRepository;
import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import com.fundit.notification.infrastructure.persistence.notification.NotificationJpaRepository;
import com.fundit.notification.infrastructure.persistence.notificationsetting.NotificationSettingJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
    private final LiveNotifyRequestJpaRepository liveNotifyRequestJpaRepository;

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

    /**
     * 신청자 수만큼 {@link #onNotificationRaised}를 재사용한다 — 새 적재 경로를 만들지 않는다.
     * 같은 {@code eventId}를 N명에게 그대로 재사용해도 {@code (event_id, member_id)} UNIQUE라
     * 안전하다(Kafka 재전송이 와도 이미 적재된 회원 것만 자연히 걸러진다).
     */
    @Override
    @Transactional
    public void onLiveStarted(LiveStartedEvent event) {
        String title = event.projectTitle() == null
                ? "신청하신 라이브 방송이 시작됐어요"
                : "「" + event.projectTitle() + "」 LIVE가 시작됐어요";
        for (UUID memberId : liveNotifyRequestJpaRepository.findMemberIdsByLiveId(event.liveId())) {
            onNotificationRaised(new NotificationRaisedEvent(
                    event.eventId(), memberId, NotifType.LIVE_START, title, "/live/" + event.liveId()));
        }
    }
}
