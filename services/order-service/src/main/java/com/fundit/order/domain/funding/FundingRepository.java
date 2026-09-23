package com.fundit.order.domain.funding;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundingRepository {

    Optional<Funding> findByPublicId(UUID publicId);

    /** ORDER-003 멱등 키 조회 — 회원 범위로 유니크. */
    Optional<Funding> findByMemberIdAndIdempotencyKey(UUID memberId, String idempotencyKey);

    /** payment-service 환불 목록(V04) 배치 조회용. */
    List<Funding> findByPublicIdIn(List<UUID> publicIds);

    Optional<Funding> findById(Long id);

    Funding save(Funding funding);

    Page<Funding> findByMemberId(UUID memberId, FundingStatus status, Pageable pageable);

    /** ORDER-013 배치 대상 조회 — payment_expires_at이 threshold 이전인 PENDING 건. */
    List<Funding> findPendingExpiredBefore(Instant threshold);

    /** ORDER-006 배치 대상 조회 — 아직 판정되지 않은(마감 전 진행 중) 해당 프로젝트의 참여 건. */
    List<Funding> findActiveByProjectId(UUID projectId);

    /** 내부 API — 알림 팬아웃 대상(펀딩 성립 후 아직 환불되지 않은 참여자)만 조회. */
    List<Funding> findGoalAchievedByProjectId(UUID projectId);

    /** #129 — 판매자 발송목록(검색어·발송상태 필터·페이지네이션). q는 blank 없이 trim된 값 또는 null. */
    Page<Funding> findSellerOrders(UUID projectId, ShippingFilter shippingFilter, String q, Pageable pageable);

    /** #129 — 판매자 발송목록 탭 건수. */
    SellerOrderShippingCounts countSellerOrdersByShippingStatus(UUID projectId);

    /**
     * #129 — fulfillment-service {@code shipment.shipped.v1} 구독 처리. 이미 채워져 있으면
     * 갱신하지 않는(idempotent) 조건부 UPDATE.
     */
    void markShipped(UUID fundingId, Instant shippedAt);
}
