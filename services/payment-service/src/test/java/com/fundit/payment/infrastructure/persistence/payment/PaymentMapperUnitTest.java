package com.fundit.payment.infrastructure.persistence.payment;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperUnitTest {

    private final PaymentMapper mapper = new PaymentMapper();

    @Test
    void 도메인과_엔티티를_왕복하면_값이_유지된다() {
        // given
        Instant now = Instant.parse("2026-09-08T01:00:00Z");
        Payment domain = Payment.builder()
                .id(UUID.randomUUID())
                .fundingId(1024L)
                .memberId(UUID.randomUUID())
                .pgOrderId("fundit-abc")
                .pgPaymentKey("pay_key")
                .pgSecret("secret")
                .amount(89_000L)
                .orderName("테스트 주문")
                .couponIssuanceId(7L)
                .paymentMethod(PaymentMethod.EASY_PAY)
                .easyPayProvider("KAKAOPAY")
                .status(PaymentStatus.COMPLETED)
                .idempotencyKey("idem")
                .paidAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // when
        PaymentJpaEntity entity = mapper.toEntity(domain);
        Payment restored = mapper.toDomain(entity);

        // then
        assertThat(restored.getId()).isEqualTo(domain.getId());
        assertThat(restored.getFundingId()).isEqualTo(1024L);
        assertThat(restored.getPgOrderId()).isEqualTo("fundit-abc");
        assertThat(restored.getPaymentMethod()).isEqualTo(PaymentMethod.EASY_PAY);
        assertThat(restored.getEasyPayProvider()).isEqualTo("KAKAOPAY");
        assertThat(restored.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(restored.getCouponIssuanceId()).isEqualTo(7L);
        assertThat(restored.getPaidAt()).isEqualTo(now);
    }

    @Test
    void 결제수단이_없으면_null로_매핑한다() {
        Payment domain = Payment.create(1L, UUID.randomUUID(), "fundit-1", 10_000L, "주문", null, "idem");

        PaymentJpaEntity entity = mapper.toEntity(domain);
        Payment restored = mapper.toDomain(entity);

        assertThat(entity.getPaymentMethod()).isNull();
        assertThat(restored.getPaymentMethod()).isNull();
        assertThat(restored.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }
}
