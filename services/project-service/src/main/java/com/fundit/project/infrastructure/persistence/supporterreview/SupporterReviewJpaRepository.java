package com.fundit.project.infrastructure.persistence.supporterreview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupporterReviewJpaRepository extends JpaRepository<SupporterReviewJpaEntity, Long> {

    List<SupporterReviewJpaEntity> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
