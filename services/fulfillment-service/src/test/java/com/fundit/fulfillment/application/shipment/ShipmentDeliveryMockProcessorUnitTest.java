package com.fundit.fulfillment.application.shipment;

import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentDeliveryMockProcessorUnitTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private FulfillmentDomainEventPublisher domainEventPublisher;

    private ShipmentDeliveryMockProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ShipmentDeliveryMockProcessor(shipmentRepository, domainEventPublisher);
    }

    @Test
    void SHIPPED_상태면_DELIVERED로_전환하고_true를_반환한다() {
        // given
        Shipment shipment = Shipment.create(1024L, 123L);
        shipment.registerShipment("CJ대한통운", "123456789012");
        when(shipmentRepository.findByFundingId(1024L)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        boolean result = processor.markDeliveredOne(1024L);

        // then
        assertThat(result).isTrue();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        verify(shipmentRepository).save(shipment);
        ArgumentCaptor<FulfillmentDomainEventPublisher.ShippingCompletedEvent> captor =
                ArgumentCaptor.forClass(FulfillmentDomainEventPublisher.ShippingCompletedEvent.class);
        verify(domainEventPublisher).publishShippingCompleted(captor.capture());
        assertThat(captor.getValue().fundingId()).isEqualTo(1024L);
        assertThat(captor.getValue().projectId()).isEqualTo(123L);
    }

    @Test
    void 이미_DELIVERED면_아무것도_하지_않고_false를_반환한다() {
        // given
        Shipment shipment = Shipment.create(1024L, 123L);
        shipment.registerShipment("CJ대한통운", "123456789012");
        shipment.markDelivered(java.time.Instant.now());
        when(shipmentRepository.findByFundingId(1024L)).thenReturn(Optional.of(shipment));

        // when
        boolean result = processor.markDeliveredOne(1024L);

        // then
        assertThat(result).isFalse();
        verify(domainEventPublisher, never()).publishShippingCompleted(any());
    }
}
