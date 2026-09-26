package com.fundit.payment.infrastructure.persistence.refund;

import com.fundit.payment.infrastructure.persistence.refund.query.RefundSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRequestJpaRepository extends JpaRepository<RefundRequestJpaEntity, Long> {

    /**
     * PAYMENT-003 — 조회 전용 프로젝션(persistence-convention.md §3). refund.refund_requests와
     * payment.payments를 payment_id 기준으로 조인한다 — 같은 데이터베이스 안의 다른 스키마이므로
     * JPQL 세타 조인으로 가능하다(PaymentERD.md 3장 — "스키마 간에는 FK를 허용"). amount는
     * payments 테이블에만 있어 조인이 필요하다.
     *
     * <p>{@code triggerType}/{@code statuses}는 둘 다 null이면 필터 없이 전체를 반환한다(V04 목록
     * 필터 — 유형/진행여부). 진행중/완료 판정은 서비스 계층에서 {@code RefundRequestStatus}를
     * 상태 목록으로 변환해 넘긴다 — 쿼리는 "이 상태 목록에 속하는지"만 안다.
     *
     * <p>{@code amount}는 화면의 "실 환불 금액"이라 결제 원금이 아니라 실제 취소된 금액이어야
     * 한다(반품비 차감 부분취소 대응). 실행 이력은 {@code payment_cancellations}에 남으므로 그
     * 합계를 쓰고, 아직 취소가 없는 건(신청 중·반려)은 결제 원금으로 폴백한다.
     */
    @Query(value = "select r.id as id, r.fundingOrderId as fundingId, r.triggerType as triggerType, "
            + "r.status as status, coalesce((select sum(c.cancelAmount) from PaymentCancellationJpaEntity c "
            + "where c.refundRequestId = r.id), p.amount) as amount, r.requestedAt as requestedAt, "
            + "r.reasonDetail as reasonDetail, r.rejectedReason as rejectedReason, r.processedAt as processedAt "
            + "from RefundRequestJpaEntity r, PaymentJpaEntity p "
            + "where p.id = r.paymentId and p.memberId = :memberId "
            + "and (:triggerType is null or r.triggerType = :triggerType) "
            + "and (:statuses is null or r.status in :statuses) "
            + "order by r.requestedAt desc",
            countQuery = "select count(r) from RefundRequestJpaEntity r, PaymentJpaEntity p "
                    + "where p.id = r.paymentId and p.memberId = :memberId "
                    + "and (:triggerType is null or r.triggerType = :triggerType) "
                    + "and (:statuses is null or r.status in :statuses)")
    Page<RefundSummaryProjection> findSummariesByMemberId(@Param("memberId") UUID memberId,
            @Param("triggerType") String triggerType, @Param("statuses") List<String> statuses, Pageable pageable);

    /**
     * 판매자 환불 목록 — DEFECT 신청 시점에 채워둔 {@code seller_id}로 직접 필터링한다(order-service
     * 재조회 없음). seller_id가 null인 유형(즉시처리 트리거)은 애초에 판매자 검토 대상이 아니라 제외된다.
     */
    @Query(value = "select r.id as id, r.fundingOrderId as fundingId, r.triggerType as triggerType, "
            + "r.status as status, coalesce((select sum(c.cancelAmount) from PaymentCancellationJpaEntity c "
            + "where c.refundRequestId = r.id), p.amount) as amount, r.requestedAt as requestedAt, "
            + "r.reasonDetail as reasonDetail, r.rejectedReason as rejectedReason, r.processedAt as processedAt "
            + "from RefundRequestJpaEntity r, PaymentJpaEntity p "
            + "where p.id = r.paymentId and r.sellerId = :sellerId order by r.requestedAt desc",
            countQuery = "select count(r) from RefundRequestJpaEntity r where r.sellerId = :sellerId")
    Page<RefundSummaryProjection> findSummariesBySellerId(@Param("sellerId") UUID sellerId, Pageable pageable);

    /** 발송 후 신청 중복 접수 차단용(반품비 차감 부분취소가 두 번 실행되는 것을 막는다). */
    Optional<RefundRequestJpaEntity> findFirstByFundingOrderIdAndTriggerTypeAndStatusOrderByRequestedAtDesc(
            UUID fundingOrderId, String triggerType, String status);

    boolean existsByFundingOrderIdAndTriggerTypeInAndStatusIn(UUID fundingOrderId, List<String> triggerTypes,
                                                              List<String> statuses);
}
