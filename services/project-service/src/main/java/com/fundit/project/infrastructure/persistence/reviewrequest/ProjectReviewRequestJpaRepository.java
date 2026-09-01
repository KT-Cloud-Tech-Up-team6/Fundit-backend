package com.fundit.project.infrastructure.persistence.reviewrequest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectReviewRequestJpaRepository extends JpaRepository<ProjectReviewRequestJpaEntity, Long> {

    Optional<ProjectReviewRequestJpaEntity> findFirstByProjectIdOrderBySubmittedAtDesc(Long projectId);
}
