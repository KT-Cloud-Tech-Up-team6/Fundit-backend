package com.fundit.payment.application.settlement;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementBatchJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementBatchJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** PAYMENT-009 — 정산 내역서 조회. 본인(해당 메이커) 배치만 조회 가능하다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementQueryService {

    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementBatchJpaRepository settlementBatchJpaRepository;
    private final OrderSettlementAggregateClient orderSettlementAggregateClient;

    /**
     * 정산 목록 — 상세(PAYMENT-009) 조회에 필요한 settlementBatchId를 확인하는 진입점.
     * 리워드 라인아이템 등 상세 집계는 담지 않는다(persistence-convention.md §3 조회 전용 프로젝션).
     */
    public Page<SettlementBatchSummary> listForSeller(UUID sellerId, Pageable pageable) {
        return settlementBatchJpaRepository.findBySellerIdOrderByIdDesc(sellerId, pageable).map(this::toSummary);
    }

    private SettlementBatchSummary toSummary(SettlementBatchJpaEntity entity) {
        return new SettlementBatchSummary(entity.getId(), entity.getBatchType(), entity.getStatus(),
                entity.getPeriodStart(), entity.getPeriodEnd(), entity.getTotalAmount());
    }

    public record SettlementBatchSummary(Long settlementBatchId, String batchType, String status,
                                          java.time.Instant periodStart, java.time.Instant periodEnd, long totalAmount) {
    }

    public SettlementDetail getDetail(UUID accountId, Long settlementBatchId) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!batch.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        List<OrderSettlementAggregateClient.LineItemAggregate> lineItems = batch.getItems().stream()
                .flatMap(item -> orderSettlementAggregateClient.fetchLineItems(item.fundingId()).stream())
                .toList();

        return new SettlementDetail(batch, lineItems);
    }

    public record SettlementDetail(SettlementBatch batch, List<OrderSettlementAggregateClient.LineItemAggregate> lineItems) {
    }
}
