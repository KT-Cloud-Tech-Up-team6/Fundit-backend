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
    private static final Long FUNDING_ID = 1024L;

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
                new OrderFundingClient.FundingSnapshot(UUID.randomUUID(), UUID.randomUUID(), "PENDING", 89_000L, "주문", null));

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
                new OrderFundingClient.FundingSnapshot(MEMBER_ID, UUID.randomUUID(), "FUNDING_IN_PROGRESS", 89_000L, "주문", null));

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
}
