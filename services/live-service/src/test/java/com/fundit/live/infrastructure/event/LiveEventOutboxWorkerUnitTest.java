package com.fundit.live.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaEntity;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveEventOutboxWorkerUnitTest {

    @Mock private LiveEventOutboxJpaRepository outboxRepository;
    @Mock private LiveEventTransport transport;

    private LiveEventOutboxWorker worker() {
        return new LiveEventOutboxWorker(outboxRepository, transport, 50);
    }

    private LiveEventOutboxJpaEntity event(String type) {
        return LiveEventOutboxJpaEntity.builder()
                .id(42L)
                .eventType(type)
                .liveSessionId(1L)
                .payload("""
                        {"liveId":"%s","projectId":"%s","occurredAt":"2026-09-10T11:00:00Z"}"""
                        .formatted(UUID.randomUUID(), UUID.randomUUID()))
                .build();
    }

    @Test
    void 발행에_성공하면_published_at을_채운다() {
        // given
        LiveEventOutboxJpaEntity e = event(LiveEventOutboxJpaEntity.TYPE_LIVE_ENDED);
        given(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).willReturn(List.of(e));

        // when
        worker().publishPending();

        // then
        verify(transport).sendLiveEnded(any());
        assertThat(e.getPublishedAt()).isNotNull();
    }

    @Test
    void 발행에_실패하면_미발행으로_남아_다음_주기에_재시도된다() {
        // given — 여기서 published_at을 채우면 행이 발행된 척 사라지고
        // 아웃박스를 둔 이유가 통째로 무력화된다(member/payment에서 실제로 고친 버그)
        LiveEventOutboxJpaEntity e = event(LiveEventOutboxJpaEntity.TYPE_LIVE_ENDED);
        given(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).willReturn(List.of(e));
        willThrow(new DependencyFailureException(new IllegalStateException("브로커 없음")))
                .given(transport).sendLiveEnded(any());

        // when
        worker().publishPending();

        // then
        assertThat(e.getPublishedAt()).isNull();
        assertThat(e.getAttemptCount()).isEqualTo(1);
        assertThat(e.getLastError()).isNotNull();
    }

    @Test
    void 한_건이_실패해도_배치의_나머지는_계속_발행한다() {
        // given
        LiveEventOutboxJpaEntity failing = event(LiveEventOutboxJpaEntity.TYPE_LIVE_ENDED);
        LiveEventOutboxJpaEntity ok = event(LiveEventOutboxJpaEntity.TYPE_LIVE_STARTED);
        given(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any()))
                .willReturn(List.of(failing, ok));
        willThrow(new DependencyFailureException(new IllegalStateException("브로커 없음")))
                .given(transport).sendLiveEnded(any());

        // when
        worker().publishPending();

        // then
        assertThat(failing.getPublishedAt()).isNull();
        assertThat(ok.getPublishedAt()).isNotNull();
    }

    @Test
    void eventId는_service_outboxId_형식이다() {
        // given — 소비 측 멱등의 유일한 근거다(event-convention.md 5번)
        LiveEventOutboxJpaEntity e = event(LiveEventOutboxJpaEntity.TYPE_LIVE_ENDED);
        given(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).willReturn(List.of(e));

        // when
        worker().publishPending();

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(LiveEventTransport.LiveEndedEvent.class);
        verify(transport).sendLiveEnded(captor.capture());
        assertThat(captor.getValue().eventId()).isEqualTo("live:42");
    }
}
