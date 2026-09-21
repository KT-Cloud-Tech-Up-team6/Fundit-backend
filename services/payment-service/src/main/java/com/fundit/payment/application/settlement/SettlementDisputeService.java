package com.fundit.payment.application.settlement;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.refund.ShippingStatusClient;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import com.fundit.payment.domain.settlement.SettlementBatchType;
import com.fundit.payment.domain.settlement.SettlementFeePolicy;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementDisputeJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementDisputeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PAYMENT-011 — 정산 이의 신청. 접수 시 대상 배치를 ON_HOLD로 전환한다(PAYMENT-015가 지급
 * 대상에서 제외).
 *
 * <p>"정산 내역서 발송일로부터 7일"(PRD 9.1.3)의 발송일 기준점은 배치 유형마다 다르다.
 * 선정산(INTERIM)은 배송 전에 생성되므로 배치 생성 시점 자체가 곧 안내 발송 시점이지만,
 * 최종정산(FINAL)은 "마지막 배송완료일+14일"이 실제 기준이라 fulfillment-service가 제공하는
 * 실제 배송완료일(deliveredAt)을 조회해서 계산한다.
 */
@Service
@RequiredArgsConstructor
public class SettlementDisputeService {

    private static final Duration DISPUTE_PERIOD = Duration.ofDays(7);

    private final SettlementBatchRepository settlementBatchRepository;
    private final SettlementDisputeJpaRepository settlementDisputeJpaRepository;
    private final ShippingStatusClient shippingStatusClient;

    @Transactional
    public DisputeResult create(UUID accountId, Long settlementBatchId, String reason, List<String> evidenceUrls) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!batch.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        Instant noticeSentAt = resolveNoticeSentAt(batch);
        if (noticeSentAt.plus(DISPUTE_PERIOD).isBefore(Instant.now())) {
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

    /**
     * 선정산은 배송 전에 생성되어 배치 생성 시점 자체가 안내 발송 시점과 같지만, 최종정산은
     * "마지막 배송완료일+14일"이 실제 발송 기준이라 fulfillment-service 조회 결과로 계산한다.
     * 최종정산 배치는 {@code shipping.completed.v1} 이벤트로만 생성되므로(PAYMENT-014) 배송완료일이
     * 없는 경우는 시스템 불변식 위반이다 — 배치 생성일로 조용히 대체하지 않고 명시적으로 실패시킨다.
     */
    private Instant resolveNoticeSentAt(SettlementBatch batch) {
        if (batch.getBatchType() != SettlementBatchType.FINAL) {
            return batch.getCreatedAt();
        }
        Instant lastDeliveredAt = batch.getItems().stream()
                .map(item -> shippingStatusClient.fetch(item.fundingId()).deliveredAt())
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElseThrow(() -> new DependencyFailureException(
                        new IllegalStateException("최종 정산 배치의 배송완료일을 확인할 수 없습니다. batchId=" + batch.getId())));
        return lastDeliveredAt.plus(SettlementFeePolicy.FINAL_SETTLEMENT_NOTICE_DELAY);
    }

    /** 접수한 이의신청의 목록·처리 상태 조회. */
    @Transactional(readOnly = true)
    public Page<DisputeSummary> listForSeller(UUID sellerId, Pageable pageable) {
        return settlementDisputeJpaRepository.findBySellerIdOrderByIdDesc(sellerId, pageable).map(this::toSummary);
    }

    /** 이의신청 상세 — 본인 접수 건만 조회 가능(S4). */
    @Transactional(readOnly = true)
    public DisputeSummary getDetail(UUID sellerId, Long disputeId) {
        SettlementDisputeJpaEntity dispute = settlementDisputeJpaRepository.findById(disputeId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!dispute.getSellerId().equals(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return toSummary(dispute);
    }

    private DisputeSummary toSummary(SettlementDisputeJpaEntity dispute) {
        return new DisputeSummary(dispute.getId(), dispute.getBatchId(), dispute.getReason(), dispute.getStatus(),
                dispute.getRequestedAt(), dispute.getResolvedAt());
    }

    public record DisputeResult(Long disputeId, String status) {
    }

    public record DisputeSummary(Long disputeId, Long settlementBatchId, String reason, String status,
                                  Instant requestedAt, Instant resolvedAt) {
    }
}
