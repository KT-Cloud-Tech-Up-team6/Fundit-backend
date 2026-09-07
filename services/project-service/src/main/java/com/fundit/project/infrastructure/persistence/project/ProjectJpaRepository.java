package com.fundit.project.infrastructure.persistence.project;

import com.fundit.project.infrastructure.persistence.project.query.ProjectListProjection;
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

    @Query("""
            select p.publicId as projectId, p.projectDisplayCode as projectDisplayCode, p.title as title,
                   p.coverImageUrl as thumbnailUrl, p.status as status, p.createdAt as createdAt,
                   p.fundingDeadline as fundingDeadline
            from ProjectJpaEntity p
            where p.sellerId = :sellerId and p.deletedAt is null
              and (:status is null or p.status = :status)
            order by p.createdAt desc
            """)
    Page<ProjectListProjection> findList(@Param("sellerId") UUID sellerId, @Param("status") String status, Pageable pageable);
}
