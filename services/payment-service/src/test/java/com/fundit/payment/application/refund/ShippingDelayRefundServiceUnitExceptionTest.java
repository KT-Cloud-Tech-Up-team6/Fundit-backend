package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingDelayRefundServiceUnitExceptionTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID FUNDING_ID = new UUID(0L, 1024L);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ShippingStatusClient shippingStatusClient;
    @Mock
    private RefundExecutionService refundExecutionService;

    private ShippingDelayRefundService shippingDelayRefundService;

    @BeforeEach
    void setUp() {
        shippingDelayRefundService = new ShippingDelayRefundService(paymentRepository, shippingStatusClient,
                refundExecutionService);
    }

    @Test
    void 완료된_결제가_없으면_NOT_FOUND다() {
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shippingDelayRefundService.requestCancel(MEMBER_ID, FUNDING_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        verifyNoInteractions(refundExecutionService);
    }

    @Test
    void 타인_결제면_FORBIDDEN이다() {
        Payment payment = Payment.create(FUNDING_ID, UUID.randomUUID(), "fundit-1", 89_000L, "주문", null, "idem");
        payment.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> shippingDelayRefundService.requestCancel(MEMBER_ID, FUNDING_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
        verifyNoInteractions(refundExecutionService);
    }

    @Test
    void 이미_발송됐으면_ALREADY_SHIPPED다() {
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-1", 89_000L, "주문", null, "idem");
        payment.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        // 이미 발송된 경우이므로 isDelayed 값과 무관하게(발송 시작 자체가 판단 기준) ALREADY_SHIPPED가 우선한다.
        when(shippingStatusClient.fetch(FUNDING_ID))
                .thenReturn(new ShippingStatusClient.ShippingStatus(true, false, null, null));

        assertThatThrownBy(() -> shippingDelayRefundService.requestCancel(MEMBER_ID, FUNDING_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.ALREADY_SHIPPED));
        verifyNoInteractions(refundExecutionService);
    }

    @Test
    void 미발송이지만_아직_지연아니면_NOT_YET_DELAYED다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-1", 89_000L, "주문", null, "idem");
        payment.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(shippingStatusClient.fetch(FUNDING_ID))
                .thenReturn(new ShippingStatusClient.ShippingStatus(false, false, null, null));

        // when
        assertThatThrownBy(() -> shippingDelayRefundService.requestCancel(MEMBER_ID, FUNDING_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.NOT_YET_DELAYED));

        // then
        verifyNoInteractions(refundExecutionService);
    }
}
