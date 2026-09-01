package com.fundit.project.infrastructure.persistence.project;

import com.fundit.project.domain.project.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<ProjectJpaEntity, Long> {

    Optional<ProjectJpaEntity> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    Optional<ProjectJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    Page<ProjectJpaEntity> findBySellerIdAndDeletedAtIsNull(UUID sellerId, Pageable pageable);

    Page<ProjectJpaEntity> findBySellerIdAndStatusInAndDeletedAtIsNull(
            UUID sellerId, Collection<ProjectStatus> statuses, Pageable pageable);
}
