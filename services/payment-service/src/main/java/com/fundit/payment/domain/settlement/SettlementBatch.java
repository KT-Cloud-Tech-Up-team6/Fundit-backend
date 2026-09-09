package com.fundit.payment.domain.settlement;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — 지급/보류 상태 전이 규칙이 있다.
 * {@link SettlementBatchItem}은 같은 애그리거트에 속한 자식이라 별도 Repository가 없다
 * (order-service Funding/FundingLineItem과 동일 패턴).
 */
@Getter
@Builder(toBuilder = true)
public class SettlementBatch {

    private final Long id;
    private final UUID sellerId;
    private final SettlementBatchType batchType;
    private SettlementBatchStatus status;
    private final Instant periodStart;
    private final Instant periodEnd;
    private final long grossAmount;
    private final long platformFeeAmount;
    private final long refundDeductionAmount;
    private final long couponDeductionAmount;
    private final long totalAmount;
    private Instant processedAt;
    private final Instant createdAt;
    private final List<SettlementBatchItem> items;

    public static SettlementBatch create(UUID sellerId, SettlementBatchType batchType, Instant periodStart,
                                          Instant periodEnd, long grossAmount, long platformFeeAmount,
                                          long refundDeductionAmount, long couponDeductionAmount,
                                          List<SettlementBatchItem> items) {
        long total = Math.max(0, grossAmount - platformFeeAmount - refundDeductionAmount - couponDeductionAmount);
        return SettlementBatch.builder()
                .sellerId(sellerId)
                .batchType(batchType)
                .status(SettlementBatchStatus.PENDING)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .grossAmount(grossAmount)
                .platformFeeAmount(platformFeeAmount)
                .refundDeductionAmount(refundDeductionAmount)
                .couponDeductionAmount(couponDeductionAmount)
                .totalAmount(total)
                .items(items)
                .build();
    }

    public boolean isOwnedBy(UUID accountId) {
        return sellerId.equals(accountId);
    }

    /** PAYMENT-011 — 이의신청 접수 시 지급 보류. */
    public void holdForDispute() {
        if (status == SettlementBatchStatus.PAID) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "이미 지급이 완료된 정산 건입니다.");
        }
        this.status = SettlementBatchStatus.ON_HOLD;
    }

    /** PAYMENT-015 — 지급 실행. ON_HOLD(이의신청 접수)는 지급 대상에서 제외한다. */
    public void markPaid() {
        if (status != SettlementBatchStatus.PENDING) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "지급 대상 상태가 아닙니다.");
        }
        this.status = SettlementBatchStatus.PAID;
        this.processedAt = Instant.now();
    }
}
