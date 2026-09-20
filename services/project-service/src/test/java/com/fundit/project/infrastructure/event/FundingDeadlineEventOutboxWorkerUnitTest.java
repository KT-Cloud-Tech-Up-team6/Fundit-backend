package com.fundit.project.infrastructure.event;

import com.fundit.project.application.project.FundingDeadlinePublisher.FundingDeadlineReachedEvent;
import com.fundit.project.infrastructure.persistence.event.FundingDeadlineEventOutboxJpaEntity;
import com.fundit.project.infrastructure.persistence.event.FundingDeadlineEventOutboxJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundingDeadlineEventOutboxWorkerUnitTest {

    @Mock
    private FundingDeadlineEventOutboxJpaRepository outboxRepository;
    @Mock
    private FundingDeadlineEventTransport transport;

    private FundingDeadlineEventOutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new FundingDeadlineEventOutboxWorker(outboxRepository, transport, 50);
    }

    @Test
    void 발행에_성공하면_published_at을_채운다() {
        // given
        FundingDeadlineEventOutboxJpaEntity event = FundingDeadlineEventOutboxJpaEntity.builder()
                .id(1L).projectId(1L).goalAmount(5_000_000L).build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any(Pageable.class))).thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        verify(transport).send(new FundingDeadlineReachedEvent(1L, 5_000_000L), 1L);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttemptCount()).isZero();
    }

    @Test
    void 발행에_실패하면_미발행으로_남기고_재시도_횟수를_올린다() {
        // given
        FundingDeadlineEventOutboxJpaEntity event = FundingDeadlineEventOutboxJpaEntity.builder()
                .id(2L).projectId(2L).goalAmount(1_000_000L).build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any(Pageable.class))).thenReturn(List.of(event));
        doThrow(new IllegalStateException("브로커 미구성")).when(transport).send(any(FundingDeadlineReachedEvent.class), anyLong());

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("브로커 미구성");
    }
}
