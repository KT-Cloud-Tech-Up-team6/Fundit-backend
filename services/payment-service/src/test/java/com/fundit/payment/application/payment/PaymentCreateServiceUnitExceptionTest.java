package com.fundit.payment.application.payment;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCreateServiceUnitExceptionTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID FUNDING_ID = new UUID(0L, 1024L);

    @Mock
    private OrderFundingClient orderFundingClient;
    @Mock
    private PaymentRepository paymentRepository;

    private PaymentCreateService paymentCreateService;

    @BeforeEach
    void setUp() {
        paymentCreateService = new PaymentCreateService(orderFundingClient, paymentRepository);
    }

    @Test
    void 본인_주문이_아니면_FORBIDDEN_예외가_발생한다() {
        // given
        when(paymentRepository.findPendingByFundingId(FUNDING_ID)).thenReturn(Optional.empty());
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(
                new OrderFundingClient.FundingSnapshot(UUID.randomUUID(), UUID.randomUUID(), "PENDING", 89_000L, "주문", null,
                        FUNDING_ID, 0L, 0L));

        // when & then
        assertThatThrownBy(() -> paymentCreateService.create(MEMBER_ID, FUNDING_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 대상_주문이_PENDING이_아니면_예외가_발생한다() {
        // given
        when(paymentRepository.findPendingByFundingId(FUNDING_ID)).thenReturn(Optional.empty());
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(
                new OrderFundingClient.FundingSnapshot(MEMBER_ID, UUID.randomUUID(), "FUNDING_IN_PROGRESS", 89_000L, "주문",
                        null, FUNDING_ID, 0L, 0L));

        // when & then
        assertThatThrownBy(() -> paymentCreateService.create(MEMBER_ID, FUNDING_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.FUNDING_NOT_PENDING));
    }

    @Test
    void 재사용_대상이_본인_결제건이_아니면_예외가_발생한다() {
        // given
        Payment existing = Payment.create(FUNDING_ID, UUID.randomUUID(), "fundit-existing", 50_000L, "기존 주문", null, "idem");
        when(paymentRepository.findPendingByFundingId(FUNDING_ID)).thenReturn(Optional.of(existing));

        // when & then
        assertThatThrownBy(() -> paymentCreateService.create(MEMBER_ID, FUNDING_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 이미_완료된_결제가_있으면_PENDING_재사용보다_먼저_거부한다() {
        // given — order-service의 결제완료 반영이 비동기라 그 창 동안 재호출된 상황을 재현
        Payment completed = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-completed", 89_000L, "완료된 주문", null, "idem");
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.of(completed));

        // when & then
        assertThatThrownBy(() -> paymentCreateService.create(MEMBER_ID, FUNDING_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.CONFLICT));
        org.mockito.Mockito.verifyNoInteractions(orderFundingClient);
        org.mockito.Mockito.verify(paymentRepository, org.mockito.Mockito.never()).findPendingByFundingId(FUNDING_ID);
    }
}
