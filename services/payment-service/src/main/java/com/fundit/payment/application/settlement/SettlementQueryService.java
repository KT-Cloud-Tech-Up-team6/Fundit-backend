package com.fundit.payment.application.settlement;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import lombok.RequiredArgsConstructor;
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
    private final OrderSettlementAggregateClient orderSettlementAggregateClient;

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
