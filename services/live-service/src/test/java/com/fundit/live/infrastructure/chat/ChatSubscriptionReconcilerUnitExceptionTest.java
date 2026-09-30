package com.fundit.live.infrastructure.chat;

import com.fundit.live.application.chat.ChatIngestService;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ChatSubscriptionReconcilerUnitExceptionTest {

    private static final Instant T0 = Instant.parse("2026-09-30T11:00:00Z");

    private final LiveSessionJpaRepository sessionRepository = mock(LiveSessionJpaRepository.class);
    private final IvsClient ivsClient = mock(IvsClient.class);
    private final ChatIngestService chatIngestService = mock(ChatIngestService.class);

    private LiveSessionJpaEntity liveSession(long id, String roomArn) {
        return LiveSessionJpaEntity.builder()
                .id(id).publicId(UUID.randomUUID()).channelId(id).projectId(UUID.randomUUID())
                .status(LiveStatus.LIVE).ivsChatRoomArn(roomArn).likeCount(0).build();
    }

    @Test
    void 구독에_실패한_방송은_건너뛰고_나머지는_연다() {
        // given — 한 방송의 IVS 오류가 다른 방송 적재를 막으면 안 된다
        given(sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE))
                .willReturn(List.of(liveSession(10L, "arn:room"), liveSession(11L, "arn:room-2")));
        given(ivsClient.createChatToken(anyString(), anyString(), any(), any())).willReturn("token");
        IvsClient.ChatConnection connection = mock(IvsClient.ChatConnection.class);
        given(connection.isOpen()).willReturn(true);
        given(ivsClient.openChatConnection(anyString(), any()))
                .willThrow(new RuntimeException("timeout"))
                .willReturn(connection);
        ChatSubscriptionReconciler reconciler = new ChatSubscriptionReconciler(
                sessionRepository, ivsClient, chatIngestService, Clock.fixed(T0, ZoneOffset.UTC));

        // when
        reconciler.reconcile();

        // then
        verify(ivsClient, times(2)).openChatConnection(any(), any());
    }
}
