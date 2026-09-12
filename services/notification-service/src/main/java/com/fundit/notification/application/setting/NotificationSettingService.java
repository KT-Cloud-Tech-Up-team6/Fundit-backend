package com.fundit.notification.application.setting;

import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import com.fundit.notification.infrastructure.persistence.notificationsetting.NotificationSettingJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 알림 유형별 수신설정(NOTI-004).
 *
 * <p>저장 구조가 "행 존재 = 수신 거부"라 설정 변경이 insert/delete로 끝난다.
 * 둘 다 idempotent하고, 기본값이 "행 없음 = 전체 수신"이라 신규 회원 초기 데이터가 필요 없다.
 */
@Service
@RequiredArgsConstructor
public class NotificationSettingService {

    private final NotificationSettingJpaRepository notificationSettingJpaRepository;

    @Transactional
    public void setEnabled(UUID memberId, NotifType notifType, boolean enabled) {
        if (enabled) {
            notificationSettingJpaRepository.deleteByMemberIdAndNotifType(memberId, notifType.name());
        } else {
            notificationSettingJpaRepository.insertIgnoringConflict(memberId, notifType.name());
        }
    }
}
