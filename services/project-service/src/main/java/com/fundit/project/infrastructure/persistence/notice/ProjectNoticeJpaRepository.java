package com.fundit.project.infrastructure.persistence.notice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectNoticeJpaRepository extends JpaRepository<ProjectNoticeJpaEntity, Long> {

    List<ProjectNoticeJpaEntity> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<ProjectNoticeJpaEntity> findByProjectIdAndNoticeTypeOrderByCreatedAtDesc(Long projectId, String noticeType);
}
