package com.fundit.payment.application.settlement;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementDisputeJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementDisputeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PAYMENT-011 — 정산 이의 신청. 접수 시 대상 배치를 ON_HOLD로 전환한다(PAYMENT-015가 지급
 * 대상에서 제외).
 *
 * <p>[가정] "발송일로부터 7일"의 발송일 기준점을 fulfillment-service 연동 전이라 알 수 없다 —
 * 배치 생성일({@code createdAt})을 대리 기준으로 사용한다. fulfillment-service 연동 후 실제
 * 발송일(shipments.shipped_at) 기준으로 교체해야 한다.
 */
@Service
@RequiredArgsConstructor
public class SettlementDisputeService {

    private static final Duration DISPUTE_PERIOD = Duration.ofDays(7);

    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementDisputeJpaRepository settlementDisputeJpaRepository;

    @Transactional
    public DisputeResult create(UUID accountId, Long settlementBatchId, String reason, List<String> evidenceUrls) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!batch.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (batch.getCreatedAt().plus(DISPUTE_PERIOD).isBefore(Instant.now())) {
            throw new BusinessException(PaymentErrorCode.DISPUTE_PERIOD_EXPIRED);
        }

        batch.holdForDispute();
        settlementBatchRepository.save(batch);

        SettlementDisputeJpaEntity saved = settlementDisputeJpaRepository.save(SettlementDisputeJpaEntity.builder()
                .batchId(settlementBatchId)
                .sellerId(accountId)
                .reason(reason)
                .evidenceUrls(evidenceUrls)
                .build());
        return new DisputeResult(saved.getId(), saved.getStatus());
    }

    public record DisputeResult(Long disputeId, String status) {
    }
}
