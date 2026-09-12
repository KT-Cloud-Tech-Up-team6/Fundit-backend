package com.fundit.fulfillment.application.shipment;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ReceiptAutoConfirmedEvent;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptAutoConfirmProcessorUnitTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private FulfillmentNotificationPublisher notificationPublisher;

    private ReceiptAutoConfirmProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ReceiptAutoConfirmProcessor(shipmentRepository, notificationPublisher);
    }

    @Test
    void DELIVERED_상태면_자동확정하고_알림을_발행한다() {
        // given
        Shipment shipment = Shipment.create(1024L, 123L);
        shipment.registerShipment("CJ대한통운", "123456789012");
        shipment.markDelivered(Instant.now());
        when(shipmentRepository.findByFundingId(1024L)).thenReturn(Optional.of(shipment));
        when(shipmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        boolean result = processor.autoConfirmOne(1024L);

        // then
        assertThat(result).isTrue();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.RECEIPT_CONFIRMED);
        assertThat(shipment.isReceiptAutoConfirmed()).isTrue();
        verify(notificationPublisher).publishReceiptAutoConfirmed(new ReceiptAutoConfirmedEvent(1024L));
    }

    @Test
    void DELIVERED가_아니면_아무것도_하지_않는다() {
        // given
        Shipment shipment = Shipment.create(1024L, 123L);
        shipment.registerShipment("CJ대한통운", "123456789012");
        when(shipmentRepository.findByFundingId(1024L)).thenReturn(Optional.of(shipment));

        // when
        boolean result = processor.autoConfirmOne(1024L);

        // then
        assertThat(result).isFalse();
    }
}
