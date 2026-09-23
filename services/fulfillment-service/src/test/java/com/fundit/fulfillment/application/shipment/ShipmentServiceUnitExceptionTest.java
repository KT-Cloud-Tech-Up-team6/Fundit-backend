package com.fundit.fulfillment.application.shipment;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher;
import com.fundit.fulfillment.application.funding.OrderFundingClient;
import com.fundit.fulfillment.application.funding.OrderFundingClient.FundingSnapshot;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.domain.FulfillmentErrorCode;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceUnitExceptionTest {

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
    void 본인_소유_프로젝트가_아니면_예외가_발생한다() {
        // given
        lenient().when(projectOwnershipClient.getSellerId(UUID.fromString("00000000-0000-0000-0000-000000000123"))).thenReturn(sellerId);

        // when & then
        assertThatThrownBy(() -> service.registerShipment(UUID.fromString("00000000-0000-0000-0000-000000000123"), UUID.fromString("00000000-0000-0000-0000-000000001024"), UUID.randomUUID(), "CJ대한통운", "123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 다른_프로젝트_소속_펀딩이면_예외가_발생한다() {
        // given
        when(projectOwnershipClient.getSellerId(UUID.fromString("00000000-0000-0000-0000-000000000123"))).thenReturn(sellerId);
        when(orderFundingClient.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(new FundingSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000999"), buyerId, UUID.randomUUID()));

        // when & then
        assertThatThrownBy(() -> service.registerShipment(UUID.fromString("00000000-0000-0000-0000-000000000123"), UUID.fromString("00000000-0000-0000-0000-000000001024"), sellerId, "CJ대한통운", "123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 이미_발송된_건에_재등록하면_예외가_발생한다() {
        // given
        when(projectOwnershipClient.getSellerId(UUID.fromString("00000000-0000-0000-0000-000000000123"))).thenReturn(sellerId);
        when(orderFundingClient.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(new FundingSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000123"), buyerId, UUID.randomUUID()));
        Shipment shipped = Shipment.create(UUID.fromString("00000000-0000-0000-0000-000000001024"), UUID.fromString("00000000-0000-0000-0000-000000000123"));
        shipped.registerShipment("CJ대한통운", "123456789012");
        when(shipmentRepository.findByFundingId(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(Optional.of(shipped));

        // when & then
        assertThatThrownBy(() -> service.registerShipment(UUID.fromString("00000000-0000-0000-0000-000000000123"), UUID.fromString("00000000-0000-0000-0000-000000001024"), sellerId, "우체국택배", "999"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.ALREADY_SHIPPED));
    }

    @Test
    void 본인_funding이_아니면_조회시_예외가_발생한다() {
        // given
        when(orderFundingClient.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(new FundingSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000123"), buyerId, UUID.randomUUID()));

        // when & then
        assertThatThrownBy(() -> service.getShipment(UUID.fromString("00000000-0000-0000-0000-000000000123"), UUID.fromString("00000000-0000-0000-0000-000000001024"), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 발송_전_상태에서_수령확인하면_예외가_발생한다() {
        // given — shipments 레코드 자체가 없음
        when(orderFundingClient.fetch(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(new FundingSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000123"), buyerId, UUID.randomUUID()));
        when(shipmentRepository.findByFundingId(UUID.fromString("00000000-0000-0000-0000-000000001024"))).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.confirmReceipt(UUID.fromString("00000000-0000-0000-0000-000000000123"), UUID.fromString("00000000-0000-0000-0000-000000001024"), buyerId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.NOT_YET_DELIVERED));
    }

    @Test
    void 이미_발송된_건은_임시저장할_수_없다() {
        // given
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        UUID fundingId = UUID.fromString("00000000-0000-0000-0000-000000001024");
        Shipment shipped = Shipment.create(fundingId, projectId);
        shipped.registerShipment("CJ대한통운", "123456789012");
        when(projectOwnershipClient.getSellerId(projectId)).thenReturn(sellerId);
        when(orderFundingClient.fetch(fundingId)).thenReturn(new FundingSnapshot(projectId, buyerId, UUID.randomUUID()));
        when(shipmentRepository.findByFundingId(fundingId)).thenReturn(Optional.of(shipped));

        // when & then
        assertThatThrownBy(() -> service.saveShippingInfo(projectId, fundingId, sellerId, "한진택배", "999"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.ALREADY_SHIPPED));
    }

    @Test
    void 본인_소유_프로젝트가_아니면_발송목록을_배치조회할_수_없다() {
        // given
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        when(projectOwnershipClient.getSellerId(projectId)).thenReturn(sellerId);

        // when & then
        assertThatThrownBy(() -> service.listForSeller(projectId, UUID.randomUUID(),
                List.of(UUID.fromString("00000000-0000-0000-0000-000000001024"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 구매자_조회는_경로의_projectId가_펀딩의_프로젝트와_다르면_거부된다() {
        // given — 소유자는 맞지만 경로 projectId가 남의 프로젝트인 경우.
        UUID fundingId = UUID.fromString("00000000-0000-0000-0000-000000001024");
        when(orderFundingClient.fetch(fundingId))
                .thenReturn(new FundingSnapshot(UUID.randomUUID(), buyerId, UUID.randomUUID()));

        // when & then
        assertThatThrownBy(() -> service.getShipment(UUID.fromString("00000000-0000-0000-0000-000000000123"), fundingId, buyerId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
    }
}
