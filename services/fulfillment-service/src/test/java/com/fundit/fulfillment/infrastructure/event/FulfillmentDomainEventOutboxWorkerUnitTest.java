package com.fundit.fulfillment.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher.ShippingCompletedEvent;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentDomainEventOutboxJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentDomainEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentDomainEventOutboxWorkerUnitTest {

    @Mock
    private FulfillmentDomainEventOutboxJpaRepository outboxRepository;
    @Mock
    private FulfillmentDomainEventTransport transport;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;

    private FulfillmentDomainEventOutboxWorker worker;

    private void setUp() {
        worker = new FulfillmentDomainEventOutboxWorker(outboxRepository, transport, projectOwnershipClient, 50);
    }

    private FulfillmentDomainEventOutboxJpaEntity event() {
        return FulfillmentDomainEventOutboxJpaEntity.builder()
                .id(1L).eventType(FulfillmentDomainEventOutboxJpaEntity.TYPE_SHIPPING_COMPLETED)
                .fundingId(1024L).projectId(123L).build();
    }

    @Test
    void 배송완료_이벤트_발행에_성공하면_판매자ID를_조회해_함께_전달하고_published_at이_채워진다() {
        // given
        setUp();
        UUID sellerId = UUID.randomUUID();
        FulfillmentDomainEventOutboxJpaEntity event = event();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));
        when(projectOwnershipClient.getSellerId(123L)).thenReturn(sellerId);

        // when
        worker.publishPending();

        // then
        ArgumentCaptor<ShippingCompletedEvent> captor = ArgumentCaptor.forClass(ShippingCompletedEvent.class);
        verify(transport).sendShippingCompleted(captor.capture(), eq(sellerId), eq(event.getCreatedAt()), any());
        assertThat(captor.getValue().fundingId()).isEqualTo(1024L);
        assertThat(captor.getValue().projectId()).isEqualTo(123L);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void 판매자ID_조회에_실패하면_재시도_횟수만_증가하고_published_at은_비워둔다() {
        // given
        setUp();
        FulfillmentDomainEventOutboxJpaEntity event = event();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));
        when(projectOwnershipClient.getSellerId(123L))
                .thenThrow(new DependencyFailureException(new RuntimeException("타임아웃")));

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void 발행_자체가_실패하면_재시도_횟수만_증가하고_published_at은_비워둔다() {
        // given
        setUp();
        UUID sellerId = UUID.randomUUID();
        FulfillmentDomainEventOutboxJpaEntity event = event();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));
        when(projectOwnershipClient.getSellerId(123L)).thenReturn(sellerId);
        doThrow(new IllegalStateException("브로커 미구성"))
                .when(transport).sendShippingCompleted(any(), any(), any(), any());

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("브로커 미구성");
    }

    @Test
    void 알_수_없는_이벤트_타입은_실패로_기록한다() {
        // given
        setUp();
        FulfillmentDomainEventOutboxJpaEntity event = FulfillmentDomainEventOutboxJpaEntity.builder()
                .id(2L).eventType("UnknownType").fundingId(1L).projectId(1L).build();
        when(outboxRepository.findByPublishedAtIsNullOrderByIdAsc(any())).thenReturn(List.of(event));

        // when
        worker.publishPending();

        // then
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).contains("알 수 없는 도메인 이벤트 타입");
    }
}
