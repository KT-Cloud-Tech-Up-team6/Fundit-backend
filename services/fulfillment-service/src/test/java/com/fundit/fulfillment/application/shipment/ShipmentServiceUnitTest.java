package com.fundit.fulfillment.application.shipment;

import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher;
import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher.ShipmentShippedEvent;
import com.fundit.fulfillment.application.funding.OrderFundingClient;
import com.fundit.fulfillment.application.funding.OrderFundingClient.FundingSnapshot;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceUnitTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private ProjectOwnershipClient projectOwnershipClient;
    @Mock
    private OrderFundingClient orderFundingClient;
    @Mock
    private FulfillmentDomainEventPublisher domainEventPublisher;

    private ShipmentService service;

    private static final UUID PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final UUID FUNDING_ID = UUID.fromString("00000000-0000-0000-0000-000000001024");

    private final UUID sellerId = UUID.randomUUID();
    private final UUID buyerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ShipmentService(shipmentRepository, projectOwnershipClient, orderFundingClient, domainEventPublisher);
    }

    @Test
    void 발송정보가_없으면_새로_생성하고_SHIPPED로_전환한다() {
        // given
        when(projectOwnershipClient.getSellerId(UUID.fromString("00000000-0000-0000-0000-000000000123"))).thenReturn(sellerId);
        when(orderFundingClient.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(new FundingSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000123"), buyerId, UUID.randomUUID()));
        when(shipmentRepository.findByFundingIdForUpdate(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(Optional.empty());
        when(shipmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        Shipment result = service.registerShipment(UUID.fromString("00000000-0000-0000-0000-000000000123"), UUID.fromString("00000000-0000-0000-0000-000000001024"), sellerId, "CJ대한통운", "123456789012");

        // then
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(result.getCarrier()).isEqualTo("CJ대한통운");
        verify(domainEventPublisher).publishShipmentShipped(new ShipmentShippedEvent(
                UUID.fromString("00000000-0000-0000-0000-000000001024"), UUID.fromString("00000000-0000-0000-0000-000000000123")));
    }

    @Test
    void 발송_전이면_저장하지_않고_PREPARING_뷰를_반환한다() {
        // given
        when(orderFundingClient.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(new FundingSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000123"), buyerId, UUID.randomUUID()));
        when(shipmentRepository.findByFundingId(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(Optional.empty());

        // when
        Shipment result = service.getShipment(UUID.fromString("00000000-0000-0000-0000-000000000123"), UUID.fromString("00000000-0000-0000-0000-000000001024"), buyerId);

        // then
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.PREPARING);
        assertThat(result.canConfirmReceipt()).isFalse();
    }

    @Test
    void 배송완료_상태에서_수령확인하면_RECEIPT_CONFIRMED로_전환된다() {
        // given
        when(orderFundingClient.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(new FundingSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000123"), buyerId, UUID.randomUUID()));
        Shipment delivered = Shipment.create(UUID.fromString("00000000-0000-0000-0000-000000001024"), UUID.fromString("00000000-0000-0000-0000-000000000123"));
        delivered.registerShipment("CJ대한통운", "123456789012");
        delivered.markDelivered(Instant.now());
        when(shipmentRepository.findByFundingIdForUpdate(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(Optional.of(delivered));
        when(shipmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        Shipment result = service.confirmReceipt(UUID.fromString("00000000-0000-0000-0000-000000000123"), UUID.fromString("00000000-0000-0000-0000-000000001024"), buyerId);

        // then
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.RECEIPT_CONFIRMED);
        assertThat(result.isReceiptAutoConfirmed()).isFalse();
    }

    @Test
    void 임시저장은_PREPARING을_유지하고_발송_이벤트를_발행하지_않는다() {
        // given
        when(projectOwnershipClient.getSellerId(PROJECT_ID)).thenReturn(sellerId);
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(new FundingSnapshot(PROJECT_ID, buyerId, UUID.randomUUID()));
        when(shipmentRepository.findByFundingIdForUpdate(FUNDING_ID)).thenReturn(Optional.empty());
        when(shipmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        Shipment result = service.saveShippingInfo(PROJECT_ID, FUNDING_ID, sellerId, "CJ대한통운", "123456789012");

        // then — 발송 처리가 아니므로 목록의 "발송 대기" 집계가 움직이면 안 된다.
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.PREPARING);
        assertThat(result.getCarrier()).isEqualTo("CJ대한통운");
        assertThat(result.getShippedAt()).isNull();
        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    void 배치조회는_행이_없는_건을_PREPARING_뷰로_채우고_다른_프로젝트_건은_제외한다() {
        // given
        UUID otherFundingId = UUID.fromString("00000000-0000-0000-0000-000000002048");
        Shipment shipped = Shipment.create(FUNDING_ID, PROJECT_ID);
        shipped.registerShipment("CJ대한통운", "123456789012");
        Shipment otherProject = Shipment.create(otherFundingId, UUID.randomUUID());
        otherProject.registerShipment("한진택배", "999999999999");
        when(projectOwnershipClient.getSellerId(PROJECT_ID)).thenReturn(sellerId);
        when(shipmentRepository.findByFundingIdIn(List.of(FUNDING_ID, otherFundingId)))
                .thenReturn(List.of(shipped, otherProject));

        // when
        List<Shipment> result = service.listForSeller(PROJECT_ID, sellerId, List.of(FUNDING_ID, otherFundingId));

        // then — 요청한 순서/건수 그대로, 남의 프로젝트 건은 빈 PREPARING 뷰로 대체된다.
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(result.get(0).getTrackingNumber()).isEqualTo("123456789012");
        assertThat(result.get(1).getStatus()).isEqualTo(ShipmentStatus.PREPARING);
        assertThat(result.get(1).getTrackingNumber()).isNull();
    }
}
