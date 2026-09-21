package com.fundit.payment.infrastructure.persistence.refund;

import com.fundit.payment.infrastructure.persistence.refund.query.RefundSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface RefundRequestJpaRepository extends JpaRepository<RefundRequestJpaEntity, Long> {

    /**
     * PAYMENT-003 — 조회 전용 프로젝션(persistence-convention.md §3). refund.refund_requests와
     * payment.payments를 payment_id 기준으로 조인한다 — 같은 데이터베이스 안의 다른 스키마이므로
     * JPQL 세타 조인으로 가능하다(PaymentERD.md 3장 — "스키마 간에는 FK를 허용"). amount는
     * payments 테이블에만 있어 조인이 필요하다.
     */
    @Query(value = "select r.id as id, r.fundingOrderId as fundingId, r.triggerType as triggerType, "
            + "r.status as status, p.amount as amount, r.requestedAt as requestedAt, "
            + "r.reasonDetail as reasonDetail, r.rejectedReason as rejectedReason, r.processedAt as processedAt "
            + "from RefundRequestJpaEntity r, PaymentJpaEntity p "
            + "where p.id = r.paymentId and p.memberId = :memberId order by r.requestedAt desc",
            countQuery = "select count(r) from RefundRequestJpaEntity r, PaymentJpaEntity p "
                    + "where p.id = r.paymentId and p.memberId = :memberId")
    Page<RefundSummaryProjection> findSummariesByMemberId(@Param("memberId") UUID memberId, Pageable pageable);

    /**
     * 판매자 환불 목록 — DEFECT 신청 시점에 채워둔 {@code seller_id}로 직접 필터링한다(order-service
     * 재조회 없음). seller_id가 null인 유형(즉시처리 트리거)은 애초에 판매자 검토 대상이 아니라 제외된다.
     */
    @Query(value = "select r.id as id, r.fundingOrderId as fundingId, r.triggerType as triggerType, "
            + "r.status as status, p.amount as amount, r.requestedAt as requestedAt, "
            + "r.reasonDetail as reasonDetail, r.rejectedReason as rejectedReason, r.processedAt as processedAt "
            + "from RefundRequestJpaEntity r, PaymentJpaEntity p "
            + "where p.id = r.paymentId and r.sellerId = :sellerId order by r.requestedAt desc",
            countQuery = "select count(r) from RefundRequestJpaEntity r where r.sellerId = :sellerId")
    Page<RefundSummaryProjection> findSummariesBySellerId(@Param("sellerId") UUID sellerId, Pageable pageable);
}
