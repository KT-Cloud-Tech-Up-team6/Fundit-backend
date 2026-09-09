package com.fundit.order.infrastructure.persistence.funding;

import com.fundit.order.infrastructure.persistence.funding.query.SupporterActivityProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundingJpaRepository extends JpaRepository<FundingJpaEntity, Long> {

    Optional<FundingJpaEntity> findByPublicId(UUID publicId);

    Page<FundingJpaEntity> findByMemberId(UUID memberId, Pageable pageable);

    Page<FundingJpaEntity> findByMemberIdAndStatus(UUID memberId, String status, Pageable pageable);

    List<FundingJpaEntity> findByStatusAndPaymentExpiresAtBefore(String status, Instant threshold);

    List<FundingJpaEntity> findByProjectIdAndStatusIn(Long projectId, List<String> statuses);

    /**
     * ORDER-001 — 취소/만료된 참여를 제외한 서포터 활동 목록(최신순). 금액은 쿠폰 할인 반영 전
     * 리워드 합산액이다[가정 — 활동 피드는 정확한 최종 결제액보다 참여 시점 스냅샷이면 충분].
     */
    @Query("select f.memberId as memberId, f.createdAt as createdAt, "
            + "coalesce(sum(li.unitPrice * li.quantity), 0) as amount "
            + "from FundingJpaEntity f join FundingLineItemJpaEntity li on li.fundingId = f.id "
            + "where f.projectId = :projectId and f.status not in ('CANCELLED_BY_MEMBER', 'PAYMENT_EXPIRED') "
            + "group by f.memberId, f.createdAt "
            + "order by f.createdAt desc")
    Page<SupporterActivityProjection> findSupporterActivity(@Param("projectId") Long projectId, Pageable pageable);
}
