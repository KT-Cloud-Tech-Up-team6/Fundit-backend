package com.fundit.payment.infrastructure.persistence.settlement;

import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchItem;
import com.fundit.payment.domain.settlement.SettlementBatchStatus;
import com.fundit.payment.domain.settlement.SettlementBatchType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class SettlementBatchMapper {

    SettlementBatch toDomain(SettlementBatchJpaEntity entity, List<SettlementBatchItemJpaEntity> itemEntities) {
        List<SettlementBatchItem> items = itemEntities.stream()
                .map(i -> new SettlementBatchItem(i.getId(), i.getFundingId(), i.getPaymentId(), i.getAmount()))
                .toList();
        return SettlementBatch.builder()
                .id(entity.getId())
                .sellerId(entity.getSellerId())
                .batchType(SettlementBatchType.valueOf(entity.getBatchType()))
                .status(SettlementBatchStatus.valueOf(entity.getStatus()))
                .periodStart(entity.getPeriodStart())
                .periodEnd(entity.getPeriodEnd())
                .grossAmount(entity.getGrossAmount())
                .platformFeeAmount(entity.getPlatformFeeAmount())
                .refundDeductionAmount(entity.getRefundDeductionAmount())
                .couponDeductionAmount(entity.getCouponDeductionAmount())
                .totalAmount(entity.getTotalAmount())
                .processedAt(entity.getProcessedAt())
                .createdAt(entity.getCreatedAt())
                .items(items)
                .build();
    }

    SettlementBatchJpaEntity toEntity(SettlementBatch domain) {
        return SettlementBatchJpaEntity.builder()
                .id(domain.getId())
                .sellerId(domain.getSellerId())
                .batchType(domain.getBatchType().name())
                .status(domain.getStatus().name())
                .periodStart(domain.getPeriodStart())
                .periodEnd(domain.getPeriodEnd())
                .grossAmount(domain.getGrossAmount())
                .platformFeeAmount(domain.getPlatformFeeAmount())
                .refundDeductionAmount(domain.getRefundDeductionAmount())
                .couponDeductionAmount(domain.getCouponDeductionAmount())
                .totalAmount(domain.getTotalAmount())
                .processedAt(domain.getProcessedAt())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    SettlementBatchItemJpaEntity toItemEntity(Long batchId, SettlementBatchItem item) {
        return SettlementBatchItemJpaEntity.builder()
                .batchId(batchId)
                .fundingId(item.fundingId())
                .paymentId(item.paymentId())
                .amount(item.amount())
                .build();
    }
}
