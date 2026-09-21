package com.fundit.project.infrastructure.persistence.notice;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectNoticeJpaRepository extends JpaRepository<ProjectNoticeJpaEntity, Long> {

    @Query("""
            select n from ProjectNoticeJpaEntity n
            where n.projectId = :projectId and (:noticeType is null or n.noticeType = :noticeType)
            """)
    Page<ProjectNoticeJpaEntity> findList(
            @Param("projectId") Long projectId, @Param("noticeType") String noticeType, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from ProjectNoticeJpaEntity n where n.id = :id")
    Optional<ProjectNoticeJpaEntity> findByIdForUpdate(@Param("id") Long id);
}
