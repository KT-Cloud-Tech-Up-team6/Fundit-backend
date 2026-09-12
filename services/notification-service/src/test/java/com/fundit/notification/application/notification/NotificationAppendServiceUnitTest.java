package com.fundit.notification.application.notification;

import com.fundit.notification.application.notification.NotificationEventListener.NotificationRaisedEvent;
import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import com.fundit.notification.infrastructure.persistence.notification.NotificationJpaRepository;
import com.fundit.notification.infrastructure.persistence.notificationsetting.NotificationSettingJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationAppendServiceUnitTest {

    @Mock
    private NotificationJpaRepository notificationJpaRepository;
    @Mock
    private NotificationSettingJpaRepository notificationSettingJpaRepository;

    @InjectMocks
    private NotificationAppendService notificationAppendService;

    private final UUID memberId = UUID.randomUUID();

    private NotificationRaisedEvent event() {
        return new NotificationRaisedEvent("evt-1", memberId, NotifType.SHIPPING_UPDATE,
                "「무선 이어폰 프로젝트」 배송이 '출고' 단계로 넘어갔어요", "/my/fundings/1/shipping");
    }

    @Test
    void 수신_거부하지_않은_유형이면_알림이_적재된다() {
        // given
        when(notificationSettingJpaRepository.existsByMemberIdAndNotifType(memberId, NotifType.SHIPPING_UPDATE))
                .thenReturn(false);

        // when
        notificationAppendService.onNotificationRaised(event());

        // then
        verify(notificationJpaRepository).insertIgnoringConflict(
                "evt-1", memberId, "SHIPPING_UPDATE",
                "「무선 이어폰 프로젝트」 배송이 '출고' 단계로 넘어갔어요", "/my/fundings/1/shipping");
    }

    @Test
    void 수신_거부한_유형이면_적재하지_않고_정상_종료한다() {
        // given — 설정 행이 존재하면 수신 거부다. 실패가 아니라 "만들지 않음"이다.
        when(notificationSettingJpaRepository.existsByMemberIdAndNotifType(memberId, NotifType.SHIPPING_UPDATE))
                .thenReturn(true);

        // when
        notificationAppendService.onNotificationRaised(event());

        // then
        verify(notificationJpaRepository, never())
                .insertIgnoringConflict(anyString(), any(), anyString(), anyString(), anyString());
    }
}
