package com.fundit.notification.application.setting;

import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import com.fundit.notification.infrastructure.persistence.notificationsetting.NotificationSettingJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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

    /**
     * 유형 전체의 현재 수신 여부. 거부 행이 없는 유형은 기본값인 수신(true)으로 채운다 —
     * 저장 구조가 "행 존재 = 거부"라 행만 돌려주면 FE가 기본값을 다시 알아야 한다.
     */
    @Transactional(readOnly = true)
    public List<SettingItem> getSettings(UUID memberId) {
        Set<NotifType> disabled = EnumSet.noneOf(NotifType.class);
        notificationSettingJpaRepository.findByMemberId(memberId)
                .forEach(setting -> disabled.add(setting.getNotifType()));
        return Arrays.stream(NotifType.values())
                .map(type -> new SettingItem(type, !disabled.contains(type)))
                .toList();
    }

    @Transactional
    public void setEnabled(UUID memberId, NotifType notifType, boolean enabled) {
        if (enabled) {
            notificationSettingJpaRepository.deleteByMemberIdAndNotifType(memberId, notifType.name());
        } else {
            notificationSettingJpaRepository.insertIgnoringConflict(memberId, notifType.name());
        }
    }

    public record SettingItem(NotifType notifType, boolean enabled) {
    }
}
