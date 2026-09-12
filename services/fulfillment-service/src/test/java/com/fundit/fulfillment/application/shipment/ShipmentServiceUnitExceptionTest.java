package com.fundit.fulfillment.application.shipment;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
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

    private ShipmentService service;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID buyerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ShipmentService(shipmentRepository, projectOwnershipClient, orderFundingClient);
    }

    @Test
    void 본인_소유_프로젝트가_아니면_예외가_발생한다() {
        // given
        lenient().when(projectOwnershipClient.getSellerId(123L)).thenReturn(sellerId);

        // when & then
        assertThatThrownBy(() -> service.registerShipment(123L, 1024L, UUID.randomUUID(), "CJ대한통운", "123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 다른_프로젝트_소속_펀딩이면_예외가_발생한다() {
        // given
        when(projectOwnershipClient.getSellerId(123L)).thenReturn(sellerId);
        when(orderFundingClient.fetch(1024L)).thenReturn(new FundingSnapshot(999L, buyerId));

        // when & then
        assertThatThrownBy(() -> service.registerShipment(123L, 1024L, sellerId, "CJ대한통운", "123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 이미_발송된_건에_재등록하면_예외가_발생한다() {
        // given
        when(projectOwnershipClient.getSellerId(123L)).thenReturn(sellerId);
        when(orderFundingClient.fetch(1024L)).thenReturn(new FundingSnapshot(123L, buyerId));
        Shipment shipped = Shipment.create(1024L, 123L);
        shipped.registerShipment("CJ대한통운", "123456789012");
        when(shipmentRepository.findByFundingId(1024L)).thenReturn(Optional.of(shipped));

        // when & then
        assertThatThrownBy(() -> service.registerShipment(123L, 1024L, sellerId, "우체국택배", "999"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.ALREADY_SHIPPED));
    }

    @Test
    void 본인_funding이_아니면_조회시_예외가_발생한다() {
        // given
        when(orderFundingClient.fetch(1024L)).thenReturn(new FundingSnapshot(123L, buyerId));

        // when & then
        assertThatThrownBy(() -> service.getShipment(123L, 1024L, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 발송_전_상태에서_수령확인하면_예외가_발생한다() {
        // given — shipments 레코드 자체가 없음
        when(orderFundingClient.fetch(1024L)).thenReturn(new FundingSnapshot(123L, buyerId));
        when(shipmentRepository.findByFundingId(1024L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.confirmReceipt(123L, 1024L, buyerId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.NOT_YET_DELIVERED));
    }
}
