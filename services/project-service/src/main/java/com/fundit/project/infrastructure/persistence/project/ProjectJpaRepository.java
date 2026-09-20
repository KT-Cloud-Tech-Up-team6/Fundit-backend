package com.fundit.project.infrastructure.persistence.project;

import com.fundit.project.infrastructure.persistence.project.query.ProjectListProjection;
import com.fundit.project.infrastructure.persistence.project.query.ProjectSummaryProjection;
import com.fundit.project.infrastructure.persistence.project.query.StatusCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     */
    @Query("""
            select p.id as id, p.publicId as projectId, p.projectDisplayCode as projectDisplayCode, p.title as title,
                   p.coverImageUrl as thumbnailUrl, p.status as status, p.createdAt as createdAt,
                   p.fundingStartAt as fundingStartAt, p.fundingDeadline as fundingDeadline,
                   p.goalAmount as goalAmount, p.categoryMajor as categoryMajor, p.categoryMinor as categoryMinor
            from ProjectJpaEntity p
            where p.sellerId = :sellerId and p.deletedAt is null
              and p.status in :statuses
              and (:q is null or lower(p.title) like lower(concat('%', :q, '%')))
            order by p.createdAt desc
            """)
    Page<ProjectListProjection> findList(@Param("sellerId") UUID sellerId, @Param("statuses") List<String> statuses,
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
}
