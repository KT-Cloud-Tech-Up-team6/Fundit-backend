package com.fundit.notification.application.notification;

import com.fundit.notification.application.notification.NotificationEventListener.LiveStartedEvent;
import com.fundit.notification.application.notification.NotificationEventListener.NotificationRaisedEvent;
import com.fundit.notification.infrastructure.persistence.livenotifyrequest.LiveNotifyRequestJpaRepository;
import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import com.fundit.notification.infrastructure.persistence.notification.NotificationJpaRepository;
import com.fundit.notification.infrastructure.persistence.notificationsetting.NotificationSettingJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationAppendServiceUnitTest {

    @Mock
    private NotificationJpaRepository notificationJpaRepository;
    @Mock
    private NotificationSettingJpaRepository notificationSettingJpaRepository;
    @Mock
    private LiveNotifyRequestJpaRepository liveNotifyRequestJpaRepository;

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

    @Test
    void 라이브_시작하면_신청자_전원에게_팬아웃한다() {
        // given
        UUID liveId = UUID.randomUUID();
        UUID member1 = UUID.randomUUID();
        UUID member2 = UUID.randomUUID();
        given(liveNotifyRequestJpaRepository.findMemberIdsByLiveId(liveId)).willReturn(List.of(member1, member2));

        // when — 신청자 수만큼 onNotificationRaised를 재사용하므로 같은 eventId가 둘에게 간다
        notificationAppendService.onLiveStarted(new LiveStartedEvent("live:1", liveId, "무선 이어폰"));

        // then
        verify(notificationJpaRepository).insertIgnoringConflict(
                "live:1", member1, "LIVE_START", "「무선 이어폰」 LIVE가 시작됐어요", "/live/" + liveId);
        verify(notificationJpaRepository).insertIgnoringConflict(
                "live:1", member2, "LIVE_START", "「무선 이어폰」 LIVE가 시작됐어요", "/live/" + liveId);
    }

    @Test
    void 프로젝트명_조회에_실패했으면_일반_문구로_대체한다() {
        // given — live-service가 조회 실패로 projectTitle을 null로 보낸 상황
        UUID liveId = UUID.randomUUID();
        given(liveNotifyRequestJpaRepository.findMemberIdsByLiveId(liveId)).willReturn(List.of(memberId));

        // when
        notificationAppendService.onLiveStarted(new LiveStartedEvent("live:2", liveId, null));

        // then
        verify(notificationJpaRepository).insertIgnoringConflict(
                "live:2", memberId, "LIVE_START", "신청하신 라이브 방송이 시작됐어요", "/live/" + liveId);
    }

    @Test
    void 신청자_중_수신_거부한_사람은_건너뛴다() {
        // given
        UUID liveId = UUID.randomUUID();
        UUID optedOut = UUID.randomUUID();
        given(liveNotifyRequestJpaRepository.findMemberIdsByLiveId(liveId)).willReturn(List.of(memberId, optedOut));
        given(notificationSettingJpaRepository.existsByMemberIdAndNotifType(memberId, NotifType.LIVE_START))
                .willReturn(false);
        given(notificationSettingJpaRepository.existsByMemberIdAndNotifType(optedOut, NotifType.LIVE_START))
                .willReturn(true);

        // when
        notificationAppendService.onLiveStarted(new LiveStartedEvent("live:3", liveId, "무선 이어폰"));

        // then
        verify(notificationJpaRepository).insertIgnoringConflict(
                "live:3", memberId, "LIVE_START", "「무선 이어폰」 LIVE가 시작됐어요", "/live/" + liveId);
        verify(notificationJpaRepository, never()).insertIgnoringConflict(
                anyString(), org.mockito.ArgumentMatchers.eq(optedOut), anyString(), anyString(), anyString());
    }

    @Test
    void 신청자가_없으면_아무것도_적재하지_않는다() {
        // given
        UUID liveId = UUID.randomUUID();
        given(liveNotifyRequestJpaRepository.findMemberIdsByLiveId(liveId)).willReturn(List.of());

        // when
        notificationAppendService.onLiveStarted(new LiveStartedEvent("live:4", liveId, "무선 이어폰"));

        // then
        verify(notificationJpaRepository, never())
                .insertIgnoringConflict(anyString(), any(), anyString(), anyString(), anyString());
    }
}
