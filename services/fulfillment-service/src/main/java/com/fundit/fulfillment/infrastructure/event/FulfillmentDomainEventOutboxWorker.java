package com.fundit.fulfillment.infrastructure.event;

import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher.ShipmentShippedEvent;
import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher.ShippingCompletedEvent;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentDomainEventOutboxJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.event.FulfillmentDomainEventOutboxJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 미발행 도메인 이벤트 아웃박스 행을 꺼내 {@link FulfillmentDomainEventTransport}로 전달한다.
 * 실패하면 published_at을 채우지 않고 attempt_count만 올려 다음 주기에 재시도한다
 * (order-service {@code FundingEventOutboxWorker}와 동일 패턴).
 */
@Component
@ConditionalOnProperty(prefix = "fulfillment-domain-event-outbox", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class FulfillmentDomainEventOutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentDomainEventOutboxWorker.class);

    private final FulfillmentDomainEventOutboxJpaRepository outboxRepository;
    private final FulfillmentDomainEventTransport transport;
    private final ProjectOwnershipClient projectOwnershipClient;
    private final int batchSize;

    public FulfillmentDomainEventOutboxWorker(FulfillmentDomainEventOutboxJpaRepository outboxRepository,
                                               FulfillmentDomainEventTransport transport,
                                               ProjectOwnershipClient projectOwnershipClient,
                                               @Value("${fulfillment-domain-event-outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.transport = transport;
        this.projectOwnershipClient = projectOwnershipClient;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${fulfillment-domain-event-outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        for (FulfillmentDomainEventOutboxJpaEntity event : outboxRepository.findByPublishedAtIsNullOrderByIdAsc(
                PageRequest.of(0, batchSize))) {
            try {
                deliver(event);
                event.markPublished();
            } catch (RuntimeException e) {
                log.warn("도메인 이벤트 발행 실패, 재시도 예정. id={} type={}", event.getId(), event.getEventType(), e);
                event.recordFailure(e.getMessage());
            }
        }
    }

    private void deliver(FulfillmentDomainEventOutboxJpaEntity event) {
        switch (event.getEventType()) {
            case FulfillmentDomainEventOutboxJpaEntity.TYPE_SHIPPING_COMPLETED -> {
                // payment-service ShippingCompletionListener가 기대하는 sellerId — fulfillment-service엔
                // 로컬에 없어(project-service 소유) 배선 계층에서 조회한다(order-service와 동일 이유).
                // 실패(타임아웃·5xx·미존재 포함) 시 DependencyFailureException이 그대로 전파되고,
                // 이 메서드를 감싼 publishPending()의 try-catch가 재시도 대상으로 기록한다.
                UUID sellerId = projectOwnershipClient.getSellerId(event.getProjectPublicId());
                transport.sendShippingCompleted(
                        new ShippingCompletedEvent(event.getFundingOrderId(), event.getProjectPublicId()),
                        sellerId, event.getCreatedAt(), event.getId());
            }
            case FulfillmentDomainEventOutboxJpaEntity.TYPE_SHIPMENT_SHIPPED -> transport.sendShipmentShipped(
                    new ShipmentShippedEvent(event.getFundingOrderId(), event.getProjectPublicId()),
                    event.getCreatedAt(), event.getId());
            default -> throw new IllegalStateException("알 수 없는 도메인 이벤트 타입: " + event.getEventType());
        }
    }
}
