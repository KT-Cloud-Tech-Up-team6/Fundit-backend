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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
        when(shipmentRepository.findByFundingId(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(Optional.empty());
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
        when(shipmentRepository.findByFundingId(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(Optional.of(delivered));
        when(shipmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        Shipment result = service.confirmReceipt(UUID.fromString("00000000-0000-0000-0000-000000000123"), UUID.fromString("00000000-0000-0000-0000-000000001024"), buyerId);

        // then
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.RECEIPT_CONFIRMED);
        assertThat(result.isReceiptAutoConfirmed()).isFalse();
    }
}
