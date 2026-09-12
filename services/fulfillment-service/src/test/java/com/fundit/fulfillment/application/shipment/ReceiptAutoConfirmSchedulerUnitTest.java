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
class ReceiptAutoConfirmSchedulerUnitTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private ReceiptAutoConfirmProcessor processor;

    private ReceiptAutoConfirmScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReceiptAutoConfirmScheduler(shipmentRepository, processor, 7);
    }

    @Test
    void 대상_건마다_프로세서를_호출한다() {
        // given
        Shipment s1 = Shipment.create(1L, 1L);
        when(shipmentRepository.findByStatusAndDeliveredAtBefore(any(ShipmentStatus.class), any()))
                .thenReturn(List.of(s1));

        // when
        scheduler.run();

        // then
        verify(processor).autoConfirmOne(1L);
    }

    @Test
    void 한_건이_실패해도_예외를_전파하지_않는다() {
        // given
        Shipment s1 = Shipment.create(1L, 1L);
        Shipment s2 = Shipment.create(2L, 1L);
        when(shipmentRepository.findByStatusAndDeliveredAtBefore(any(ShipmentStatus.class), any()))
                .thenReturn(List.of(s1, s2));
        doThrow(new RuntimeException("실패")).when(processor).autoConfirmOne(1L);

        // when
        scheduler.run();

        // then
        verify(processor, times(1)).autoConfirmOne(2L);
    }
}
