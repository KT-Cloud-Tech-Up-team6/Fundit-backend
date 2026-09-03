package com.fundit.member.infrastructure.persistence.wish;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface WishJpaRepository extends JpaRepository<WishJpaEntity, Long> {

    Page<WishJpaEntity> findByMemberId(UUID memberId, Pageable pageable);

    /**
     * 찜 등록은 idempotent해야 한다(CLAUDE.md 핵심 설계 결정) — 중복 등록·재시도를
     * 에러로 처리하지 않기 위해 유니크 인덱스(uq_wishes_member_project) 대상
     * ON CONFLICT DO NOTHING으로 처리한다. @Modifying 쿼리는 호출부가 트랜잭션
     * 안에 있어야 동작한다(WishService의 @Transactional에 의존).
     */
    @Modifying
    @Query(value = "INSERT INTO wishes (member_id, project_id, created_at) "
            + "VALUES (:memberId, :projectId, now()) "
            + "ON CONFLICT (member_id, project_id) DO NOTHING", nativeQuery = true)
    void insertIgnoringConflict(@Param("memberId") UUID memberId, @Param("projectId") Long projectId);

    /** 찜 해제도 idempotent해야 한다 — 이미 없는 대상 삭제도 정상(영향 행 0)으로 취급. */
    @Modifying
    @Query(value = "DELETE FROM wishes WHERE member_id = :memberId AND project_id = :projectId", nativeQuery = true)
    void deleteByMemberIdAndProjectId(@Param("memberId") UUID memberId, @Param("projectId") Long projectId);
}
