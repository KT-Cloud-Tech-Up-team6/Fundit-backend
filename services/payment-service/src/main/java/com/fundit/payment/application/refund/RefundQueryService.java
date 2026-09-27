package com.fundit.payment.application.refund;

import com.fundit.payment.domain.refund.RefundRequestStatus;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.infrastructure.persistence.refund.RefundRequestJpaRepository;
import com.fundit.payment.infrastructure.persistence.refund.query.RefundSummaryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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

    private static final List<String> IN_PROGRESS_STATUSES = List.of(RefundRequestStatus.REQUESTED.name(),
            RefundRequestStatus.UNDER_REVIEW.name(), RefundRequestStatus.APPROVED.name(),
            RefundRequestStatus.PROCESSING.name());
    private static final List<String> DONE_STATUSES = List.of(RefundRequestStatus.COMPLETED.name(),
            RefundRequestStatus.REJECTED.name());

    private final RefundRequestJpaRepository refundRequestJpaRepository;
    private final OrderSummaryClient orderSummaryClient;

    /**
     * V04 — 프로젝트명·상품/옵션은 order-service를 페이지 단위로 한 번만 배치 조회해 채운다.
     * {@code triggerTypes}/{@code inProgress}는 둘 다 선택값(null이면 필터 없음) — 진행중은
     * 완료/반려 전 상태 전부(REQUESTED/UNDER_REVIEW/APPROVED/PROCESSING)로 판정한다.
     *
     * <p>{@code triggerTypes}가 목록인 이유: 화면의 "유형" 드롭다운 한 칸이 트리거 여러 개를
     * 묶는다(취소 = 참여취소·발송지연·목표미달, 반품 = 하자·구매자귀책반품). 묶음의 정의는
     * 화면 몫이라 서버는 값 목록만 받는다 — 그룹 enum을 두면 화면이 바뀔 때마다 서버가 바뀐다.
     */
    public Page<RefundSummary> listMyRefunds(UUID accountId, List<RefundTriggerType> triggerTypes, Boolean inProgress,
                                              Pageable pageable) {
        List<String> statuses = inProgress == null ? null : (inProgress ? IN_PROGRESS_STATUSES : DONE_STATUSES);
        Page<RefundSummaryProjection> page = refundRequestJpaRepository.findSummariesByMemberId(accountId,
                toNames(triggerTypes), statuses, pageable);
        Map<UUID, OrderSummaryClient.OrderSummary> orderSummaries = orderSummaryClient.fetchBatch(
                page.getContent().stream().map(RefundSummaryProjection::getFundingId).distinct().toList());
        return page.map(projection -> toView(projection, orderSummaries.get(projection.getFundingId())));
    }

    /**
     * 빈 목록은 null로 바꿔 넘긴다 — JPQL {@code in :list}에 빈 컬렉션을 주면 프로바이더에 따라
     * 문법 오류가 되거나 "아무것도 매칭되지 않음"이 되어, 파라미터를 생략한 것과 결과가 달라진다.
     */
    private List<String> toNames(List<RefundTriggerType> triggerTypes) {
        if (triggerTypes == null || triggerTypes.isEmpty()) {
            return null;
        }
        return triggerTypes.stream().map(RefundTriggerType::name).toList();
    }

    /** 판매자 환불 목록 — 소유 프로젝트가 아니라 본인이 sellerId로 등록된 신청만(DEFECT만 해당). */
    public Page<RefundSummary> listForSeller(UUID sellerId, Pageable pageable) {
        Page<RefundSummaryProjection> page = refundRequestJpaRepository.findSummariesBySellerId(sellerId, pageable);
        Map<UUID, OrderSummaryClient.OrderSummary> orderSummaries = orderSummaryClient.fetchBatch(
                page.getContent().stream().map(RefundSummaryProjection::getFundingId).distinct().toList());
        return page.map(projection -> toView(projection, orderSummaries.get(projection.getFundingId())));
    }

    /**
     * order-service 펀딩 내역용 내부 조회 — 주문별 신청 이력(유형·상태). 여기서는 order-service를
     * 되짚어 조회하지 않는다(호출 주체가 order-service다).
     */
    public List<FundingRefundStatus> listByFundingIds(List<UUID> fundingIds) {
        if (fundingIds.isEmpty()) {
            return List.of();
        }
        return refundRequestJpaRepository.findByFundingOrderIdInOrderByRequestedAtDesc(fundingIds).stream()
                .map(entity -> new FundingRefundStatus(entity.getFundingOrderId(), entity.getId(),
                        entity.getTriggerType(), entity.getStatus(), entity.getRequestedAt()))
                .toList();
    }

    private RefundSummary toView(RefundSummaryProjection projection, OrderSummaryClient.OrderSummary orderSummary) {
        return new RefundSummary(projection.getId(), projection.getFundingId(), projection.getTriggerType(),
                projection.getStatus(), projection.getAmount(), projection.getRequestedAt(),
                projection.getReasonDetail(), projection.getRejectedReason(), projection.getProcessedAt(),
                orderSummary);
    }

    /** 주문별 신청 이력 한 건(최신순) — 신청 화면 재진입 링크에 쓰라고 {@code refundId}까지 준다. */
    public record FundingRefundStatus(UUID fundingId, Long refundId, String triggerType, String status,
                                       Instant requestedAt) {
    }

    /** {@code orderSummary}는 order-service 조회 실패 시 null일 수 있다(부가 정보, degrade). */
    public record RefundSummary(Long refundId, UUID fundingId, String triggerType, String status, long amount,
                                 Instant requestedAt, String reasonDetail, String rejectedReason,
                                 Instant completedAt, OrderSummaryClient.OrderSummary orderSummary) {
    }
}
