package com.fundit.payment.domain.payment;

import com.fundit.common.error.BusinessException;
import com.fundit.payment.domain.PaymentErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentUnitExceptionTest {

    private Payment newPendingPayment() {
        return Payment.create(1024L, UUID.randomUUID(), "fundit-abc123", 89_000L, "테스트 주문", null, "idem-key-1");
    }

    @Test
    void 스냅샷_금액과_다르면_금액불일치_예외가_발생한다() {
        // given
        Payment payment = newPendingPayment();

        // when & then
        assertThatThrownBy(() -> payment.verifyAmount(1_000L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    @Test
    void PENDING이_아니면_승인시_예외가_발생한다() {
        // given
        Payment payment = newPendingPayment();
        payment.markCompleted("pay_key_1", "secret_1", PaymentMethod.CARD, null, Instant.now());

        // when & then
        assertThatThrownBy(() -> payment.markCompleted("pay_key_2", "secret_2", PaymentMethod.CARD, null, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_PENDING);
    }

    @Test
    void PENDING이_아니면_실패처리시_예외가_발생한다() {
        // given
        Payment payment = newPendingPayment();
        payment.markFailed();

        // when & then
        assertThatThrownBy(payment::markFailed)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.PAYMENT_NOT_PENDING);
    }

    @Test
    void COMPLETED가_아니면_취소시_예외가_발생한다() {
        // given
        Payment payment = newPendingPayment();

        // when & then
        assertThatThrownBy(payment::markCancelled)
                .isInstanceOf(BusinessException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
