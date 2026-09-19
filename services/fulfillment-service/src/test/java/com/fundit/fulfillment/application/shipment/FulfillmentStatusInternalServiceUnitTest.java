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
import java.util.List;
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
        Shipment shipment = Shipment.create(UUID.fromString("00000000-0000-0000-0000-000000001024"), UUID.fromString("00000000-0000-0000-0000-000000000123"));
        shipment.registerShipment("CJ대한통운", "123456789012");
        shipment.markDelivered(Instant.now());
        when(shipmentRepository.findByFundingId(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(Optional.of(shipment));

        // when
        var view = service.getStatus(UUID.fromString("00000000-0000-0000-0000-000000001024"));

        // then
        assertThat(view.isAlreadyShipped()).isTrue();
        assertThat(view.isDelayed()).isFalse();
        assertThat(view.deliveredAt()).isNotNull();
    }

    @Test
    void 미발송이고_발송예정일이_지났으면_지연으로_판정한다() {
        // given
        when(shipmentRepository.findByFundingId(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(Optional.empty());
        when(orderFundingClient.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(new FundingSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000123"), UUID.randomUUID(), UUID.randomUUID()));
        FulfillmentTracker tracker = FulfillmentTracker.create(UUID.fromString("00000000-0000-0000-0000-000000000123")).toBuilder().id(1L).build();
        when(trackerRepository.findByProjectId(UUID.fromString("00000000-0000-0000-0000-000000000123"))).thenReturn(Optional.of(tracker));
        when(stageDetailJpaRepository.findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(1L, "SHIPPING_OUT"))
                .thenReturn(Optional.of(FulfillmentStageDetailJpaEntity.builder()
                        .id(1L).trackerId(1L).stage("SHIPPING_OUT")
                        .plannedEndAt(Instant.now().minus(1, ChronoUnit.DAYS))
                        .detailText("d").build()));

        // when
        var view = service.getStatus(UUID.fromString("00000000-0000-0000-0000-000000001024"));

        // then
        assertThat(view.isAlreadyShipped()).isFalse();
        assertThat(view.isDelayed()).isTrue();
    }

    @Test
    void 미발송이고_발송예정일_전이면_지연이_아니다() {
        // given
        when(shipmentRepository.findByFundingId(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(Optional.empty());
        when(orderFundingClient.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(new FundingSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000123"), UUID.randomUUID(), UUID.randomUUID()));
        FulfillmentTracker tracker = FulfillmentTracker.create(UUID.fromString("00000000-0000-0000-0000-000000000123")).toBuilder().id(1L).build();
        when(trackerRepository.findByProjectId(UUID.fromString("00000000-0000-0000-0000-000000000123"))).thenReturn(Optional.of(tracker));
        when(stageDetailJpaRepository.findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(1L, "SHIPPING_OUT"))
                .thenReturn(Optional.of(FulfillmentStageDetailJpaEntity.builder()
                        .id(1L).trackerId(1L).stage("SHIPPING_OUT")
                        .plannedEndAt(Instant.now().plus(1, ChronoUnit.DAYS))
                        .detailText("d").build()));

        // when
        var view = service.getStatus(UUID.fromString("00000000-0000-0000-0000-000000001024"));

        // then
        assertThat(view.isDelayed()).isFalse();
    }

    @Test
    void 미발송이고_예상일정_정보가_없으면_지연이_아닌_것으로_보수적으로_판정한다() {
        // given
        when(shipmentRepository.findByFundingId(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(Optional.empty());
        when(orderFundingClient.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(new FundingSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000123"), UUID.randomUUID(), UUID.randomUUID()));
        when(trackerRepository.findByProjectId(UUID.fromString("00000000-0000-0000-0000-000000000123"))).thenReturn(Optional.empty());

        // when
        var view = service.getStatus(UUID.fromString("00000000-0000-0000-0000-000000001024"));

        // then
        assertThat(view.isDelayed()).isFalse();
    }

    @Test
    void 레거시_Long_PK는_orderId로_해석한_뒤_조회한다() {
        // given
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000001024");
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        when(orderFundingClient.fetchByInternalId(1024L))
                .thenReturn(new FundingSnapshot(projectId, UUID.randomUUID(), orderId));
        when(shipmentRepository.findByFundingId(orderId)).thenReturn(Optional.empty());
        when(orderFundingClient.fetch(orderId)).thenReturn(new FundingSnapshot(projectId, UUID.randomUUID(), orderId));
        when(trackerRepository.findByProjectId(projectId)).thenReturn(Optional.empty());

        // when
        var view = service.getStatus(1024L);

        // then
        assertThat(view.isAlreadyShipped()).isFalse();
        assertThat(view.isDelayed()).isFalse();
    }

    @Test
    void 배치_조회시_발송된_건과_미발송_건을_구분한다() {
        // given
        UUID shippedFundingId = UUID.fromString("00000000-0000-0000-0000-000000001024");
        UUID unshippedFundingId = UUID.fromString("00000000-0000-0000-0000-000000002048");
        Shipment shipment = Shipment.create(shippedFundingId, UUID.randomUUID());
        shipment.registerShipment("CJ대한통운", "123456789012");
        Instant deliveredAt = Instant.now();
        shipment.markDelivered(deliveredAt);
        when(shipmentRepository.findByFundingIdIn(List.of(shippedFundingId, unshippedFundingId)))
                .thenReturn(List.of(shipment));

        // when
        var result = service.getStatuses(List.of(shippedFundingId, unshippedFundingId));

        // then
        assertThat(result).hasSize(2);
        var shipped = result.stream().filter(r -> r.fundingId().equals(shippedFundingId)).findFirst().orElseThrow();
        assertThat(shipped.isAlreadyShipped()).isTrue();
        assertThat(shipped.deliveredAt()).isEqualTo(deliveredAt);
        var unshipped = result.stream().filter(r -> r.fundingId().equals(unshippedFundingId)).findFirst().orElseThrow();
        assertThat(unshipped.isAlreadyShipped()).isFalse();
        assertThat(unshipped.deliveredAt()).isNull();
    }

    @Test
    void 빈_목록으로_배치_조회하면_리포지토리를_호출하지_않는다() {
        // when
        var result = service.getStatuses(List.of());

        // then
        assertThat(result).isEmpty();
    }
}
