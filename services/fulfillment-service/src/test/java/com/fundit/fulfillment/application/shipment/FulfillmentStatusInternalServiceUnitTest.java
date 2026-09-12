package com.fundit.fulfillment.application.shipment;

import com.fundit.fulfillment.application.funding.OrderFundingClient;
import com.fundit.fulfillment.application.funding.OrderFundingClient.FundingSnapshot;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentStatusInternalServiceUnitTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private OrderFundingClient orderFundingClient;
    @Mock
    private FulfillmentTrackerRepository trackerRepository;
    @Mock
    private FulfillmentStageDetailJpaRepository stageDetailJpaRepository;

    private FulfillmentStatusInternalService service;

    @BeforeEach
    void setUp() {
        service = new FulfillmentStatusInternalService(shipmentRepository, orderFundingClient, trackerRepository,
                stageDetailJpaRepository);
    }

    @Test
    void shipments_레코드가_있으면_그_값을_그대로_사용한다() {
        // given
        Shipment shipment = Shipment.create(1024L, 123L);
        shipment.registerShipment("CJ대한통운", "123456789012");
        shipment.markDelivered(Instant.now());
        when(shipmentRepository.findByFundingId(1024L)).thenReturn(Optional.of(shipment));

        // when
        var view = service.getStatus(1024L);

        // then
        assertThat(view.isAlreadyShipped()).isTrue();
        assertThat(view.isDelayed()).isFalse();
        assertThat(view.deliveredAt()).isNotNull();
    }

    @Test
    void 미발송이고_발송예정일이_지났으면_지연으로_판정한다() {
        // given
        when(shipmentRepository.findByFundingId(1024L)).thenReturn(Optional.empty());
        when(orderFundingClient.fetch(1024L)).thenReturn(new FundingSnapshot(123L, UUID.randomUUID()));
        FulfillmentTracker tracker = FulfillmentTracker.create(123L).toBuilder().id(1L).build();
        when(trackerRepository.findByProjectId(123L)).thenReturn(Optional.of(tracker));
        when(stageDetailJpaRepository.findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(1L, "SHIPPING_OUT"))
                .thenReturn(Optional.of(FulfillmentStageDetailJpaEntity.builder()
                        .id(1L).trackerId(1L).stage("SHIPPING_OUT")
                        .plannedEndAt(Instant.now().minus(1, ChronoUnit.DAYS))
                        .detailText("d").build()));

        // when
        var view = service.getStatus(1024L);

        // then
        assertThat(view.isAlreadyShipped()).isFalse();
        assertThat(view.isDelayed()).isTrue();
    }

    @Test
    void 미발송이고_발송예정일_전이면_지연이_아니다() {
        // given
        when(shipmentRepository.findByFundingId(1024L)).thenReturn(Optional.empty());
        when(orderFundingClient.fetch(1024L)).thenReturn(new FundingSnapshot(123L, UUID.randomUUID()));
        FulfillmentTracker tracker = FulfillmentTracker.create(123L).toBuilder().id(1L).build();
        when(trackerRepository.findByProjectId(123L)).thenReturn(Optional.of(tracker));
        when(stageDetailJpaRepository.findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(1L, "SHIPPING_OUT"))
                .thenReturn(Optional.of(FulfillmentStageDetailJpaEntity.builder()
                        .id(1L).trackerId(1L).stage("SHIPPING_OUT")
                        .plannedEndAt(Instant.now().plus(1, ChronoUnit.DAYS))
                        .detailText("d").build()));

        // when
        var view = service.getStatus(1024L);

        // then
        assertThat(view.isDelayed()).isFalse();
    }

    @Test
    void 미발송이고_예상일정_정보가_없으면_지연이_아닌_것으로_보수적으로_판정한다() {
        // given
        when(shipmentRepository.findByFundingId(1024L)).thenReturn(Optional.empty());
        when(orderFundingClient.fetch(1024L)).thenReturn(new FundingSnapshot(123L, UUID.randomUUID()));
        when(trackerRepository.findByProjectId(123L)).thenReturn(Optional.empty());

        // when
        var view = service.getStatus(1024L);

        // then
        assertThat(view.isDelayed()).isFalse();
    }
}
