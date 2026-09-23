package com.fundit.project.infrastructure.persistence.project;

import com.fundit.project.infrastructure.persistence.project.query.ProjectListProjection;
import com.fundit.project.infrastructure.persistence.project.query.ProjectSummaryProjection;
import com.fundit.project.infrastructure.persistence.project.query.StatusCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<ProjectJpaEntity, Long> {

    Optional<ProjectJpaEntity> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Optional<ProjectJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    List<ProjectJpaEntity> findBySellerIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID sellerId);

    /**
     * statuses는 항상 비어있지 않은 값으로 넘긴다(status 미지정 시 서비스 계층에서 전체 상태를
     * 채워 넘김) — JPA 파라미터를 collection IN절과 null 체크 양쪽에 걸쳐 쓰는 걸 피하기 위함.
     *
     * <p>q(검색어) 유무로 쿼리를 분리한다 — {@code (:q is null or lower(p.title) like lower(concat(...)))}
     * 형태는 PostgreSQL이 {@code concat} 표현식 타입을 플래닝 시점에 정해야 해서 q가 null이면
     * bytea로 잘못 추론돼 {@code lower(bytea) does not exist}로 500이 난다(런타임 값과 무관하게
     * 쿼리 플래닝 시점 타입 추론 문제라 null 가드로 못 막음). q 없는 쪽은 concat을 아예 쓰지 않는다.
     */
    @Query("""
            select p.id as id, p.publicId as projectId, p.projectDisplayCode as projectDisplayCode, p.title as title,
                   p.coverImageUrl as thumbnailUrl, p.status as status, p.createdAt as createdAt,
                   p.fundingStartAt as fundingStartAt, p.fundingDeadline as fundingDeadline,
                   p.goalAmount as goalAmount, p.categoryMajor as categoryMajor, p.categoryMinor as categoryMinor
            from ProjectJpaEntity p
            where p.sellerId = :sellerId and p.deletedAt is null
              and p.status in :statuses
            order by p.createdAt desc
            """)
    Page<ProjectListProjection> findList(@Param("sellerId") UUID sellerId, @Param("statuses") List<String> statuses,
                                          Pageable pageable);

    @Query("""
            select p.id as id, p.publicId as projectId, p.projectDisplayCode as projectDisplayCode, p.title as title,
                   p.coverImageUrl as thumbnailUrl, p.status as status, p.createdAt as createdAt,
                   p.fundingStartAt as fundingStartAt, p.fundingDeadline as fundingDeadline,
                   p.goalAmount as goalAmount, p.categoryMajor as categoryMajor, p.categoryMinor as categoryMinor
            from ProjectJpaEntity p
            where p.sellerId = :sellerId and p.deletedAt is null
              and p.status in :statuses
              and lower(p.title) like lower(concat('%', :q, '%'))
            order by p.createdAt desc
            """)
    Page<ProjectListProjection> findListByTitle(@Param("sellerId") UUID sellerId,
                                                 @Param("statuses") List<String> statuses,
                                                 @Param("q") String q, Pageable pageable);

    @Query("""
            select p.status as status, count(p) as count
            from ProjectJpaEntity p
            where p.sellerId = :sellerId and p.deletedAt is null
            group by p.status
            """)
    List<StatusCountProjection> countBySellerIdGroupByStatus(@Param("sellerId") UUID sellerId);

    /** order-service 내부 API({@code GET /internal/projects/summaries})용 배치 조회. */
    @Query("""
            select p.publicId as publicId, p.sellerId as sellerId, p.title as title,
                   p.coverImageUrl as thumbnailUrl
            from ProjectJpaEntity p
            where p.publicId in :publicIds and p.deletedAt is null
            """)
    List<ProjectSummaryProjection> findSummariesByPublicIdIn(@Param("publicIds") List<UUID> publicIds);

    /**
     * FundingDeadlineWatcher 배치 대상 — 마감이 지났는데 아직 통지하지 않은 진행중 프로젝트.
     * {@code Pageable}로 배치 크기를 제한한다(마감이 한 번에 몰려도 한 트랜잭션에서 전부 읽지 않음).
     * id 오름차순으로 고정해야 페이지마다 겹치거나 빠지는 행 없이 안정적으로 순회한다.
     */
    List<ProjectJpaEntity> findByStatusAndFundingDeadlineLessThanEqualAndDeadlineNotifiedAtIsNullAndDeletedAtIsNullOrderByIdAsc(
            String status, Instant now, Pageable pageable);
}
