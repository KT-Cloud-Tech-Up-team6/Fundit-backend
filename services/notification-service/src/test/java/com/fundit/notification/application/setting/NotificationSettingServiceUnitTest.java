package com.fundit.notification.application.setting;

import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import com.fundit.notification.infrastructure.persistence.notificationsetting.NotificationSettingJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

/** "행 존재 = 수신 거부" 구조라 설정 변경이 insert/delete로 끝난다. */
@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceUnitTest {

    @Mock
    private NotificationSettingJpaRepository notificationSettingJpaRepository;

    @InjectMocks
    private NotificationSettingService notificationSettingService;

    @Test
    void 수신을_끄면_거부_행이_삽입된다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        notificationSettingService.setEnabled(memberId, NotifType.SHIPPING_UPDATE, false);

        // then
        verify(notificationSettingJpaRepository).insertIgnoringConflict(memberId, "SHIPPING_UPDATE");
    }

    @Test
    void 수신을_켜면_거부_행이_삭제된다() {
        // given
        UUID memberId = UUID.randomUUID();

        // when
        notificationSettingService.setEnabled(memberId, NotifType.SHIPPING_UPDATE, true);

        // then
        verify(notificationSettingJpaRepository).deleteByMemberIdAndNotifType(memberId, "SHIPPING_UPDATE");
    }
}
