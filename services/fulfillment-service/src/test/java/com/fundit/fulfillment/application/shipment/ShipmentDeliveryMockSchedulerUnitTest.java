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
        Shipment s1 = Shipment.create(1L, 1L);
        Shipment s2 = Shipment.create(2L, 1L);
        when(shipmentRepository.findByStatusAndShippedAtBefore(any(ShipmentStatus.class), any()))
                .thenReturn(List.of(s1, s2));
        when(processor.markDeliveredOne(1L)).thenReturn(true);
        when(processor.markDeliveredOne(2L)).thenReturn(true);

        // when
        scheduler.run();

        // then
        verify(processor).markDeliveredOne(1L);
        verify(processor).markDeliveredOne(2L);
    }

    @Test
    void 한_건이_실패해도_나머지는_계속_처리한다() {
        // given
        Shipment s1 = Shipment.create(1L, 1L);
        Shipment s2 = Shipment.create(2L, 1L);
        when(shipmentRepository.findByStatusAndShippedAtBefore(any(ShipmentStatus.class), any()))
                .thenReturn(List.of(s1, s2));
        doThrow(new RuntimeException("실패")).when(processor).markDeliveredOne(1L);

        // when
        scheduler.run();

        // then
        verify(processor, times(1)).markDeliveredOne(2L);
    }
}
