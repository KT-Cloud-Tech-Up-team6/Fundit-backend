package com.fundit.payment.domain.payment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentUnitTest {

    private static final Long FUNDING_ID = 1024L;
    private static final UUID MEMBER_ID = UUID.randomUUID();

    private Payment newPendingPayment() {
        return Payment.create(FUNDING_ID, MEMBER_ID, "fundit-abc123", 89_000L, "테스트 주문", 7L, "idem-key-1");
    }

    @Test
    void 결제_시도를_생성하면_PENDING_상태다() {
        // when
        Payment payment = newPendingPayment();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getId()).isNotNull();
        assertThat(payment.getFundingId()).isEqualTo(FUNDING_ID);
        assertThat(payment.getAmount()).isEqualTo(89_000L);
        assertThat(payment.getCouponIssuanceId()).isEqualTo(7L);
    }

    @Test
    void 본인_결제건이면_소유권_검증을_통과한다() {
        // given
        Payment payment = newPendingPayment();

        // when & then
        assertThat(payment.isOwnedBy(MEMBER_ID)).isTrue();
        assertThat(payment.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Nested
    class 승인_처리 {

        @Test
        void PENDING_상태에서_승인하면_COMPLETED로_전환된다() {
            // given
            Payment payment = newPendingPayment();
            Instant paidAt = Instant.now();

            // when
            payment.markCompleted("pay_key_1", "secret_1", PaymentMethod.EASY_PAY, "KAKAOPAY", paidAt);

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(payment.getPgPaymentKey()).isEqualTo("pay_key_1");
            assertThat(payment.getPgSecret()).isEqualTo("secret_1");
            assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.EASY_PAY);
            assertThat(payment.getEasyPayProvider()).isEqualTo("KAKAOPAY");
            assertThat(payment.getPaidAt()).isEqualTo(paidAt);
            assertThat(payment.isCompleted()).isTrue();
        }

        @Test
        void 스냅샷_금액과_같으면_검증을_통과한다() {
            // given
            Payment payment = newPendingPayment();

            // when & then — 예외가 발생하지 않아야 한다
            payment.verifyAmount(89_000L);
        }
    }

    @Nested
    class 취소_처리 {

        @Test
        void COMPLETED_상태에서_취소하면_CANCELLED로_전환된다() {
            // given
            Payment payment = newPendingPayment();
            payment.markCompleted("pay_key_1", "secret_1", PaymentMethod.CARD, null, Instant.now());

            // when
            payment.markCancelled();

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        }
    }

    @Nested
    class 실패_처리 {

        @Test
        void PENDING_상태에서_실패하면_FAILED로_전환된다() {
            // given
            Payment payment = newPendingPayment();

            // when
            payment.markFailed();

            // then
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }
    }
}
