package com.fundit.project.infrastructure.persistence.liveverification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveQuestionSummaryJpaRepository extends JpaRepository<LiveQuestionSummaryJpaEntity, Long> {

    Optional<LiveQuestionSummaryJpaEntity> findByProjectIdAndQuestionSummaryId(Long projectId, String questionSummaryId);

    List<LiveQuestionSummaryJpaEntity> findByProjectIdOrderByQuestionCountDesc(Long projectId);
}
