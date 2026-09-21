package com.fundit.payment.application.refund;

import com.fundit.payment.infrastructure.persistence.refund.RefundRequestJpaRepository;
import com.fundit.payment.infrastructure.persistence.refund.query.RefundSummaryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
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
    private final OrderSummaryClient orderSummaryClient;

    /** V04 — 프로젝트명·상품/옵션은 order-service를 페이지 단위로 한 번만 배치 조회해 채운다. */
    public Page<RefundSummary> listMyRefunds(UUID accountId, Pageable pageable) {
        Page<RefundSummaryProjection> page = refundRequestJpaRepository.findSummariesByMemberId(accountId, pageable);
        Map<UUID, OrderSummaryClient.OrderSummary> orderSummaries = orderSummaryClient.fetchBatch(
                page.getContent().stream().map(RefundSummaryProjection::getFundingId).distinct().toList());
        return page.map(projection -> toView(projection, orderSummaries.get(projection.getFundingId())));
    }

    /** 판매자 환불 목록 — 소유 프로젝트가 아니라 본인이 sellerId로 등록된 신청만(DEFECT만 해당). */
    public Page<RefundSummary> listForSeller(UUID sellerId, Pageable pageable) {
        Page<RefundSummaryProjection> page = refundRequestJpaRepository.findSummariesBySellerId(sellerId, pageable);
        Map<UUID, OrderSummaryClient.OrderSummary> orderSummaries = orderSummaryClient.fetchBatch(
                page.getContent().stream().map(RefundSummaryProjection::getFundingId).distinct().toList());
        return page.map(projection -> toView(projection, orderSummaries.get(projection.getFundingId())));
    }

    private RefundSummary toView(RefundSummaryProjection projection, OrderSummaryClient.OrderSummary orderSummary) {
        return new RefundSummary(projection.getId(), projection.getFundingId(), projection.getTriggerType(),
                projection.getStatus(), projection.getAmount(), projection.getRequestedAt(),
                projection.getReasonDetail(), projection.getRejectedReason(), projection.getProcessedAt(),
                orderSummary);
    }

    /** {@code orderSummary}는 order-service 조회 실패 시 null일 수 있다(부가 정보, degrade). */
    public record RefundSummary(Long refundId, UUID fundingId, String triggerType, String status, long amount,
                                 Instant requestedAt, String reasonDetail, String rejectedReason,
                                 Instant completedAt, OrderSummaryClient.OrderSummary orderSummary) {
    }
}
