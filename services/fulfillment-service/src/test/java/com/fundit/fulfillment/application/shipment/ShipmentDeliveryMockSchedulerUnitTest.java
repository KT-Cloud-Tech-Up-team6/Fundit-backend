package com.fundit.fulfillment.application.shipment;

import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentDeliveryMockSchedulerUnitTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private ShipmentDeliveryMockProcessor processor;

    private ShipmentDeliveryMockScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ShipmentDeliveryMockScheduler(shipmentRepository, processor, 3);
    }

    @Test
    void 대상_건마다_프로세서를_호출한다() {
        // given
        Shipment s1 = Shipment.create(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Shipment s2 = Shipment.create(UUID.fromString("00000000-0000-0000-0000-000000000002"), UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(shipmentRepository.findByStatusAndShippedAtBefore(any(ShipmentStatus.class), any()))
                .thenReturn(List.of(s1, s2));
        when(processor.markDeliveredOne(UUID.fromString("00000000-0000-0000-0000-000000000001"))).thenReturn(true);
        when(processor.markDeliveredOne(UUID.fromString("00000000-0000-0000-0000-000000000002"))).thenReturn(true);

        // when
        scheduler.run();

        // then
        verify(processor).markDeliveredOne(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        verify(processor).markDeliveredOne(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    }

    @Test
    void 한_건이_실패해도_나머지는_계속_처리한다() {
        // given
        Shipment s1 = Shipment.create(UUID.fromString("00000000-0000-0000-0000-000000000001"), UUID.fromString("00000000-0000-0000-0000-000000000001"));
        Shipment s2 = Shipment.create(UUID.fromString("00000000-0000-0000-0000-000000000002"), UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(shipmentRepository.findByStatusAndShippedAtBefore(any(ShipmentStatus.class), any()))
                .thenReturn(List.of(s1, s2));
        doThrow(new RuntimeException("실패")).when(processor).markDeliveredOne(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        // when
        scheduler.run();

        // then
        verify(processor, times(1)).markDeliveredOne(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    }
}
