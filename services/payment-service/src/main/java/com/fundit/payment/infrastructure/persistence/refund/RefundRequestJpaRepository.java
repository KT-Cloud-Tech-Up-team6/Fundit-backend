package com.fundit.payment.infrastructure.persistence.refund;

import com.fundit.payment.infrastructure.persistence.refund.query.RefundSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /** 결제당 진행 중인 취소 요청은 최대 1건이다({@code uq_refund_requests_cancel_in_flight}, V11). */
    Optional<RefundRequestJpaEntity> findByPaymentIdAndStatusAndCancelAmountIsNotNull(UUID paymentId, String status);

    /** 대사 배치 목록 — 오래된 요청 순. 해결하지 못한 건은 요청 시각을 미뤄 뒤로 보낸다({@link #deferCancelRequest}). */
    List<RefundRequestJpaEntity> findByStatusAndCancelAmountIsNotNullAndCancelRequestedAtBeforeOrderByCancelRequestedAtAsc(
            String status, Instant before, Pageable pageable);

    /**
     * 아직 "취소 요청됨"일 때만 요청 시각을 미룬다(조건부 UPDATE). 대사 중 요청이 되돌려졌거나(REQUESTED·삭제)
     * 확정됐을 수 있어, 조건 없이 갱신하면 끝난 행을 다시 건드린다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefundRequestJpaEntity r set r.cancelRequestedAt = :at "
            + "where r.id = :id and r.status = 'PROCESSING' and r.cancelAmount is not null")
    int deferCancelRequest(@Param("id") Long id, @Param("at") Instant at);

    /**
     * 멈춘 취소 요청의 선점(비교 후 갱신). 동시에 들어오면 뒤의 UPDATE가 행 잠금을 기다렸다가 바뀐 요청 시각으로
     * 조건을 다시 평가해 0행이 된다. 시각은 같음이 아니라 기준 이전인지로 비교한다 — DB(마이크로초)와
     * {@code Instant}(나노초)의 정밀도 차이로 같음 비교가 어긋나지 않게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefundRequestJpaEntity r set r.cancelRequestedAt = :now "
            + "where r.id = :id and r.status = 'PROCESSING' and r.cancelAmount is not null "
            + "and r.cancelRequestedAt < :staleBefore")
    int claimCancelRequest(@Param("id") Long id, @Param("staleBefore") Instant staleBefore, @Param("now") Instant now);

    boolean existsByFundingOrderIdAndTriggerTypeInAndStatusIn(UUID fundingOrderId, List<String> triggerTypes,
                                                              List<String> statuses);

    /**
     * order-service 펀딩 내역(V03/V06)의 "신청 여부" 표시용 배치 조회 — 주문 카드 버튼이
     * 신청 후 "취소 내역"/"반품·교환 내역"으로 바뀌어야 해서 진행 중인 신청까지 알아야 한다
     * (order-service는 완료 이벤트만 구독하므로 REQUESTED/UNDER_REVIEW 건을 모른다).
     */
    List<RefundRequestJpaEntity> findByFundingOrderIdInOrderByRequestedAtDesc(List<UUID> fundingOrderIds);
}
