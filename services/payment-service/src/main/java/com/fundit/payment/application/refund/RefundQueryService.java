package com.fundit.payment.application.refund;

import com.fundit.payment.infrastructure.persistence.refund.RefundRequestJpaRepository;
import com.fundit.payment.infrastructure.persistence.refund.query.RefundSummaryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * PAYMENT-003 — 환불 신청/처리 통합 내역 조회. 순수 조회 전용(도메인 로직 없음)이라
 * persistence-convention.md §3에 따라 application이 프로젝션 JpaRepository를 직접 쓴다
 * (order-service SupporterActivityService와 동일 패턴).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundQueryService {

    private final RefundRequestJpaRepository refundRequestJpaRepository;

    public Page<RefundSummary> listMyRefunds(UUID accountId, Pageable pageable) {
        return refundRequestJpaRepository.findSummariesByMemberId(accountId, pageable).map(this::toView);
    }

    private RefundSummary toView(RefundSummaryProjection projection) {
        return new RefundSummary(projection.getId(), projection.getFundingId(), projection.getTriggerType(),
                projection.getStatus(), projection.getAmount(), projection.getRequestedAt());
    }

    public record RefundSummary(Long refundId, Long fundingId, String triggerType, String status, long amount,
                                 Instant requestedAt) {
    }
}
