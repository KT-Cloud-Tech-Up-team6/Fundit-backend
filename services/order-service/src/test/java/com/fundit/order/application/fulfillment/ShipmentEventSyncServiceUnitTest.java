package com.fundit.order.application.fulfillment;

import com.fundit.order.application.fulfillment.ShipmentEventListener.ShipmentShippedEvent;
import com.fundit.order.domain.funding.FundingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipmentEventSyncServiceUnitTest {

    @Mock
    private FundingRepository fundingRepository;

    @InjectMocks
    private ShipmentEventSyncService service;

    @Test
    void 발송시작_이벤트를_받으면_fundings에_발송시각을_반영한다() {
        // given
        UUID fundingId = UUID.randomUUID();
        Instant shippedAt = Instant.now();
        ShipmentShippedEvent event = new ShipmentShippedEvent(fundingId, UUID.randomUUID(), shippedAt);

        // when
        service.onShipmentShipped(event);

        // then
        verify(fundingRepository).markShipped(fundingId, shippedAt);
    }
}
