package com.fundit.project.infrastructure.persistence.notice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectNoticeCommentJpaRepository extends JpaRepository<ProjectNoticeCommentJpaEntity, Long> {

    List<ProjectNoticeCommentJpaEntity> findByNoticeIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long noticeId);
}
