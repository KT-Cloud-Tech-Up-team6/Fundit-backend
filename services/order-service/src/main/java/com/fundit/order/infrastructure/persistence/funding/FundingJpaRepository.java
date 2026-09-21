package com.fundit.order.infrastructure.persistence.funding;

import com.fundit.order.infrastructure.persistence.funding.query.SupporterActivityProjection;
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

public interface FundingJpaRepository extends JpaRepository<FundingJpaEntity, Long> {

    Optional<FundingJpaEntity> findByPublicId(UUID publicId);

    /** payment-service 환불 목록(V04) 배치 조회용. */
    List<FundingJpaEntity> findByPublicIdIn(List<UUID> publicIds);

    /**
     * PROJECT-015 펀딩 집계 배치 대상 — 참여자로 셀 수 있는(결제완료, 미환불) 펀딩이 하나라도
     * 있는 프로젝트만 순회한다. PENDING(미결제)·취소·환불 건은 통계에서 제외한다.
     *
     * <p><b>레거시 {@code project_id}(Long)가 아니라 {@code project_public_id}(UUID)로 조회한다.</b>
     * cross-service ID 통일(#69) 이후 {@link com.fundit.order.infrastructure.persistence.funding.FundingMapper}가
     * 신규 펀딩에 레거시 컬럼을 더 이상 채우지 않아, 그 컬럼으로 조회하면 항상 빈 목록이 나와
     * 이 배치가 실질적으로 아무 것도 처리하지 못한다.
     */
    @Query(value = "SELECT DISTINCT project_public_id FROM fundings "
            + "WHERE status IN ('FUNDING_IN_PROGRESS','GOAL_ACHIEVED') AND project_public_id IS NOT NULL",
            nativeQuery = true)
    List<UUID> findDistinctProjectPublicIdsWithCountableFundings();

    Page<FundingJpaEntity> findByMemberId(UUID memberId, Pageable pageable);

    Page<FundingJpaEntity> findByMemberIdAndStatus(UUID memberId, String status, Pageable pageable);

    List<FundingJpaEntity> findByStatusAndPaymentExpiresAtBefore(String status, Instant threshold);

    /** ORDER-006/내부 API — project-service publicId(UUID) 기준. */
    List<FundingJpaEntity> findByProjectPublicIdAndStatusIn(UUID projectPublicId, List<String> statuses);

    /**
     * ORDER-001 — 취소/만료된 참여를 제외한 서포터 활동 목록(최신순). 금액은 쿠폰 할인 반영 전
     * 리워드 합산액이다[가정 — 활동 피드는 정확한 최종 결제액보다 참여 시점 스냅샷이면 충분].
     */
    @Query("select f.memberId as memberId, f.createdAt as createdAt, "
            + "coalesce(sum(li.unitPrice * li.quantity), 0) as amount "
            + "from FundingJpaEntity f join FundingLineItemJpaEntity li on li.fundingId = f.id "
            + "where f.projectPublicId = :projectId and f.status not in ('CANCELLED_BY_MEMBER', 'PAYMENT_EXPIRED') "
            + "group by f.memberId, f.createdAt "
            + "order by f.createdAt desc")
    Page<SupporterActivityProjection> findSupporterActivity(@Param("projectId") UUID projectId, Pageable pageable);

    /** cross-service ID 통일(#69) 백필 대상 — 레거시 Long project_id는 있지만 UUID가 아직 안 채워진 행. */
    List<FundingJpaEntity> findByProjectIdIsNotNullAndProjectPublicIdIsNull();

    @Modifying
    @Query("update FundingJpaEntity f set f.projectPublicId = :projectPublicId where f.id = :id")
    void updateProjectPublicId(@Param("id") Long id, @Param("projectPublicId") UUID projectPublicId);
}
