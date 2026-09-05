package com.fundit.project.infrastructure.persistence.notice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectNoticeCommentJpaRepository extends JpaRepository<ProjectNoticeCommentJpaEntity, Long> {

    Page<ProjectNoticeCommentJpaEntity> findByNoticeIdAndDeletedAtIsNull(Long noticeId, Pageable pageable);
}
