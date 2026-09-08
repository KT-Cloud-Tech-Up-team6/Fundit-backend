package com.fundit.order.application.order;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.funding.FundingEventPublisher;
import com.fundit.order.domain.OrderErrorCode;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.order.domain.inventory.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancelServiceUnitExceptionTest {

    @Mock
    private FundingRepository fundingRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private FundingEventPublisher fundingEventPublisher;

    @InjectMocks
    private OrderCancelService orderCancelService;

    @Test
    void 존재하지_않는_주문이면_NOT_FOUND_예외가_발생한다() {
        // given
        UUID orderId = UUID.randomUUID();
        when(fundingRepository.findByPublicId(orderId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderCancelService.cancel(UUID.randomUUID(), orderId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 본인_주문이_아니면_FORBIDDEN_예외가_발생한다() {
        // given
        UUID orderId = UUID.randomUUID();
        Funding funding = Funding.builder().id(1L).publicId(orderId).memberId(UUID.randomUUID()).projectId(10L)
                .status(FundingStatus.PENDING).shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(0L).paymentExpiresAt(Instant.now().plusSeconds(1800)).lineItems(List.of())
                .createdAt(Instant.now()).build();
        when(fundingRepository.findByPublicId(orderId)).thenReturn(Optional.of(funding));

        // when & then
        assertThatThrownBy(() -> orderCancelService.cancel(UUID.randomUUID(), orderId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 이미_목표판정이_끝난_주문은_ORDER_NOT_CANCELLABLE_예외가_발생한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Funding funding = Funding.builder().id(1L).publicId(orderId).memberId(memberId).projectId(10L)
                .status(FundingStatus.GOAL_ACHIEVED).shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(0L).paymentExpiresAt(Instant.now().plusSeconds(1800)).lineItems(List.of())
                .createdAt(Instant.now()).build();
        when(fundingRepository.findByPublicId(orderId)).thenReturn(Optional.of(funding));

        // when & then
        assertThatThrownBy(() -> orderCancelService.cancel(memberId, orderId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(OrderErrorCode.ORDER_NOT_CANCELLABLE));
    }
}
