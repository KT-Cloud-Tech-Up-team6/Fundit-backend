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
     * <p>{@code triggerTypes}/{@code statuses}는 둘 다 null이면 필터 없이 전체를 반환한다(V04 목록
     * 필터 — 유형/진행여부). 진행중/완료 판정은 서비스 계층에서 {@code RefundRequestStatus}를
     * 상태 목록으로 변환해 넘긴다 — 쿼리는 "이 상태 목록에 속하는지"만 안다. 유형도 목록인 이유는
     * 화면의 유형 한 칸이 트리거 여러 개를 묶기 때문이다(빈 목록은 서비스에서 null로 정규화).
     *
     * <p>{@code amount}는 화면의 "실 환불 금액"이라 결제 원금이 아니라 실제 취소된 금액이어야
     * 한다(반품비 차감 부분취소 대응). 실행 이력은 {@code payment_cancellations}에 남으므로 그
     * 합계를 쓰고, 아직 취소가 없는 건(신청 중·반려)은 결제 원금으로 폴백한다. 교환은 환불이 없으므로 0이다
     * (원금으로 폴백하면 교환 카드에 결제 원금이 환불액처럼 보인다).
     */
    @Query(value = "select r.id as id, r.fundingOrderId as fundingId, r.triggerType as triggerType, "
            + "r.status as status, case when r.triggerType = 'EXCHANGE' then 0L else "
            + "coalesce((select sum(c.cancelAmount) from PaymentCancellationJpaEntity c "
            + "where c.refundRequestId = r.id), p.amount) end as amount, r.requestedAt as requestedAt, "
            + "r.reasonDetail as reasonDetail, r.rejectedReason as rejectedReason, r.processedAt as processedAt "
            + "from RefundRequestJpaEntity r, PaymentJpaEntity p "
            + "where p.id = r.paymentId and p.memberId = :memberId "
            + "and (:triggerTypes is null or r.triggerType in :triggerTypes) "
            + "and (:statuses is null or r.status in :statuses) "
            + "order by r.requestedAt desc",
            countQuery = "select count(r) from RefundRequestJpaEntity r, PaymentJpaEntity p "
                    + "where p.id = r.paymentId and p.memberId = :memberId "
                    + "and (:triggerTypes is null or r.triggerType in :triggerTypes) "
                    + "and (:statuses is null or r.status in :statuses)")
    Page<RefundSummaryProjection> findSummariesByMemberId(@Param("memberId") UUID memberId,
            @Param("triggerTypes") List<String> triggerTypes, @Param("statuses") List<String> statuses,
            Pageable pageable);

    /**
     * 판매자 환불 목록 — DEFECT 신청 시점에 채워둔 {@code seller_id}로 직접 필터링한다(order-service
     * 재조회 없음). seller_id가 null인 유형(즉시처리 트리거)은 애초에 판매자 검토 대상이 아니라 제외된다.
     */
    @Query(value = "select r.id as id, r.fundingOrderId as fundingId, r.triggerType as triggerType, "
            + "r.status as status, case when r.triggerType = 'EXCHANGE' then 0L else "
            + "coalesce((select sum(c.cancelAmount) from PaymentCancellationJpaEntity c "
            + "where c.refundRequestId = r.id), p.amount) end as amount, r.requestedAt as requestedAt, "
            + "r.reasonDetail as reasonDetail, r.rejectedReason as rejectedReason, r.processedAt as processedAt "
            + "from RefundRequestJpaEntity r, PaymentJpaEntity p "
            + "where p.id = r.paymentId and r.sellerId = :sellerId order by r.requestedAt desc",
            countQuery = "select count(r) from RefundRequestJpaEntity r where r.sellerId = :sellerId")
    Page<RefundSummaryProjection> findSummariesBySellerId(@Param("sellerId") UUID sellerId, Pageable pageable);

    /** 발송 후 신청 중복 접수 차단용(반품비 차감 부분취소가 두 번 실행되는 것을 막는다). */
    Optional<RefundRequestJpaEntity> findFirstByFundingOrderIdAndTriggerTypeAndStatusOrderByRequestedAtDesc(
            UUID fundingOrderId, String triggerType, String status);

    List<RefundRequestJpaEntity> findByTriggerTypeAndStatusAndReshipmentRequestedAtIsNullOrderByIdAsc(
            String triggerType, String status, Pageable pageable);

    boolean existsByFundingOrderIdAndTriggerTypeInAndStatusIn(UUID fundingOrderId, List<String> triggerTypes,
                                                              List<String> statuses);

    /**
     * order-service 펀딩 내역(V03/V06)의 "신청 여부" 표시용 배치 조회 — 주문 카드 버튼이
     * 신청 후 "취소 내역"/"반품·교환 내역"으로 바뀌어야 해서 진행 중인 신청까지 알아야 한다
     * (order-service는 완료 이벤트만 구독하므로 REQUESTED/UNDER_REVIEW 건을 모른다).
     */
    List<RefundRequestJpaEntity> findByFundingOrderIdInOrderByRequestedAtDesc(List<UUID> fundingOrderIds);
}
