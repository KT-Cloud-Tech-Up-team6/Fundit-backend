package com.fundit.payment.infrastructure.persistence.payment;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
class PaymentMapper {

    Payment toDomain(PaymentJpaEntity entity) {
        return Payment.builder()
                .id(entity.getId())
                .fundingId(entity.getFundingId())
                .memberId(entity.getMemberId())
                .pgOrderId(entity.getPgOrderId())
                .pgPaymentKey(entity.getPgPaymentKey())
                .pgSecret(entity.getPgSecret())
                .amount(entity.getAmount())
                .orderName(entity.getOrderName())
                .couponIssuanceId(entity.getCouponIssuanceId())
                .paymentMethod(entity.getPaymentMethod() == null ? null : PaymentMethod.valueOf(entity.getPaymentMethod()))
                .easyPayProvider(entity.getEasyPayProvider())
                .status(PaymentStatus.valueOf(entity.getStatus()))
                .idempotencyKey(entity.getIdempotencyKey())
                .paidAt(entity.getPaidAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    PaymentJpaEntity toEntity(Payment domain) {
        return PaymentJpaEntity.builder()
                .id(domain.getId())
                .fundingId(domain.getFundingId())
                .memberId(domain.getMemberId())
                .pgOrderId(domain.getPgOrderId())
                .pgPaymentKey(domain.getPgPaymentKey())
                .pgSecret(domain.getPgSecret())
                .amount(domain.getAmount())
                .orderName(domain.getOrderName())
                .couponIssuanceId(domain.getCouponIssuanceId())
                .paymentMethod(domain.getPaymentMethod() == null ? null : domain.getPaymentMethod().name())
                .easyPayProvider(domain.getEasyPayProvider())
                .status(domain.getStatus().name())
                .idempotencyKey(domain.getIdempotencyKey())
                .paidAt(domain.getPaidAt())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
