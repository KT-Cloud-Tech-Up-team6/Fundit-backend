package com.fundit.live.infrastructure.chat;

import com.fundit.live.application.chat.ChatIngestService;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ChatSubscriptionReconcilerUnitTest {

    private static final Instant T0 = Instant.parse("2026-09-30T11:00:00Z");

    private final LiveSessionJpaRepository sessionRepository = mock(LiveSessionJpaRepository.class);
    private final IvsClient ivsClient = mock(IvsClient.class);
    private final ChatIngestService chatIngestService = mock(ChatIngestService.class);

    private final LiveSessionJpaEntity session = LiveSessionJpaEntity.builder()
            .id(10L).publicId(UUID.randomUUID()).channelId(1L).projectId(UUID.randomUUID())
            .status(LiveStatus.LIVE).ivsChatRoomArn("arn:room").likeCount(0).build();

    private ChatSubscriptionReconciler reconcilerAt(Instant now) {
        return new ChatSubscriptionReconciler(sessionRepository, ivsClient, chatIngestService,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void givenLive(LiveSessionJpaEntity... sessions) {
        given(sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE)).willReturn(List.of(sessions));
    }

    private IvsClient.ChatConnection givenConnection(boolean open) {
        IvsClient.ChatConnection connection = mock(IvsClient.ChatConnection.class);
        given(connection.isOpen()).willReturn(open);
        return connection;
    }

    @Nested
    class 연결_관리 {

        @Test
        void 진행_중인_방송은_보기_전용_토큰으로_구독한다() {
            // given
            givenLive(session);
            given(ivsClient.createChatToken(anyString(), anyString(), any(), any())).willReturn("token");
            IvsClient.ChatConnection connection = givenConnection(true);
            given(ivsClient.openChatConnection(anyString(), any())).willReturn(connection);

            // when
            reconcilerAt(T0).reconcile();

            // then — 빈 권한 = 보기 전용. 구독용 연결이 메시지를 보낼 일은 없다
            verify(ivsClient).createChatToken("arn:room", ChatSubscriptionReconciler.INGEST_USER_ID, List.of(), Map.of());
            verify(ivsClient).openChatConnection(any(), any());
        }

        @Test
        void 열려_있는_연결은_다시_열지_않는다() {
            // given
            givenLive(session);
            given(ivsClient.createChatToken(anyString(), anyString(), any(), any())).willReturn("token");
            IvsClient.ChatConnection connection = givenConnection(true);
            given(ivsClient.openChatConnection(anyString(), any())).willReturn(connection);
            ChatSubscriptionReconciler reconciler = reconcilerAt(T0);

            // when
            reconciler.reconcile();
            reconciler.reconcile();

            // then
            verify(ivsClient, times(1)).openChatConnection(any(), any());
        }

        @Test
        void 끊긴_연결은_다음_주기에_다시_연다() {
            // given
            givenLive(session);
            given(ivsClient.createChatToken(anyString(), anyString(), any(), any())).willReturn("token");
            IvsClient.ChatConnection dropped = givenConnection(false);
            IvsClient.ChatConnection fresh = givenConnection(true);
            given(ivsClient.openChatConnection(anyString(), any())).willReturn(dropped, fresh);
            ChatSubscriptionReconciler reconciler = reconcilerAt(T0);

            // when
            reconciler.reconcile();
            reconciler.reconcile();

            // then
            verify(ivsClient, times(2)).openChatConnection(any(), any());
        }

        @Test
        void 세션_만료_전에_새_연결을_연_뒤_기존_연결을_닫는다() {
            // given — IVS 채팅 세션 기본 수명 60분
            givenLive(session);
            given(ivsClient.createChatToken(anyString(), anyString(), any(), any())).willReturn("token");
            IvsClient.ChatConnection old = givenConnection(true);
            IvsClient.ChatConnection fresh = givenConnection(true);
            given(ivsClient.openChatConnection(anyString(), any())).willReturn(old, fresh);
            Clock clock = mock(Clock.class);
            given(clock.instant()).willReturn(T0, T0.plus(ChatSubscriptionReconciler.RENEW_AFTER).plus(Duration.ofSeconds(1)));
            ChatSubscriptionReconciler reconciler =
                    new ChatSubscriptionReconciler(sessionRepository, ivsClient, chatIngestService, clock);

            // when
            reconciler.reconcile();
            reconciler.reconcile();

            // then
            verify(ivsClient, times(2)).openChatConnection(any(), any());
            verify(old).close();
            verify(fresh, never()).close();
        }

        @Test
        void 끝난_방송의_연결은_닫는다() {
            // given
            given(ivsClient.createChatToken(anyString(), anyString(), any(), any())).willReturn("token");
            IvsClient.ChatConnection connection = givenConnection(true);
            given(ivsClient.openChatConnection(anyString(), any())).willReturn(connection);
            given(sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE))
                    .willReturn(List.of(session), List.of());
            ChatSubscriptionReconciler reconciler = reconcilerAt(T0);

            // when
            reconciler.reconcile();
            reconciler.reconcile();

            // then
            verify(connection).close();
        }

        @Test
        void 구독에_실패한_방송은_건너뛰고_나머지는_연다() {
            // given — 한 방송의 IVS 오류가 다른 방송 적재를 막으면 안 된다
            LiveSessionJpaEntity other = LiveSessionJpaEntity.builder()
                    .id(11L).publicId(UUID.randomUUID()).channelId(2L).projectId(UUID.randomUUID())
                    .status(LiveStatus.LIVE).ivsChatRoomArn("arn:room-2").likeCount(0).build();
            givenLive(session, other);
            given(ivsClient.createChatToken(anyString(), anyString(), any(), any())).willReturn("token");
            given(ivsClient.openChatConnection(anyString(), any()))
                    .willThrow(new RuntimeException("timeout"))
                    .willReturn(givenConnection(true));

            // when
            reconcilerAt(T0).reconcile();

            // then
            verify(ivsClient, times(2)).openChatConnection(any(), any());
        }
    }

    @Nested
    class 메시지_적재 {

        @SuppressWarnings("unchecked")
        private Consumer<IvsClient.ChatMessage> subscribe() {
            givenLive(session);
            given(ivsClient.createChatToken(anyString(), anyString(), any(), any())).willReturn("token");
            IvsClient.ChatConnection connection = givenConnection(true);
            ArgumentCaptor<Consumer<IvsClient.ChatMessage>> listener = ArgumentCaptor.forClass(Consumer.class);
            given(ivsClient.openChatConnection(anyString(), listener.capture())).willReturn(connection);
            reconcilerAt(T0).reconcile();
            return listener.getValue();
        }

        @Test
        void 받은_메시지를_채팅방_기준으로_적재한다() {
            // given
            Consumer<IvsClient.ChatMessage> onMessage = subscribe();
            UUID senderId = UUID.randomUUID();

            // when
            onMessage.accept(new IvsClient.ChatMessage("msg-1", senderId.toString(), "사이즈가 어떻게 되나요?", T0));

            // then
            verify(chatIngestService).ingest("arn:room", "msg-1", senderId, "사이즈가 어떻게 되나요?", T0);
        }

        @Test
        void 회원이_아닌_발신자는_적재하지_않는다() {
            // given
            Consumer<IvsClient.ChatMessage> onMessage = subscribe();

            // when
            onMessage.accept(new IvsClient.ChatMessage("msg-1", "guest-abc", "안녕하세요", T0));

            // then
            verifyNoInteractions(chatIngestService);
        }
    }
}
