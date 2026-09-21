package com.fundit.member.infrastructure.persistence.wish;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface WishJpaRepository extends JpaRepository<WishJpaEntity, Long> {

    /**
     * 찜 목록 — 프로젝트 UUID·제목·썸네일은 project_snapshots(이벤트 구독으로 채움)에서 가져온다.
     * left join이라 스냅샷이 아직 없는 프로젝트도 찜 목록에서 빠지지 않는다.
     *
     * <p>정렬을 쿼리에 박아두는 이유: 컨트롤러가 정렬 없는 PageRequest를 넘기는데 ORDER BY가 없으면
     * 순서가 임의가 되어 같은 행이 두 페이지에 나오거나 빠진다. createdAt은 동률이 가능해 PK를 2차 키로 둔다.
     */
    @Query(value = """
            select new com.fundit.member.infrastructure.persistence.wish.WishView(
                w.projectId, s.projectPublicId, s.title, s.thumbnailUrl, w.createdAt)
            from WishJpaEntity w
            left join com.fundit.member.infrastructure.persistence.projectsnapshot.ProjectSnapshotJpaEntity s
                on s.projectId = w.projectId
            where w.memberId = :memberId
            order by w.createdAt desc, w.id desc
            """,
            countQuery = "select count(w) from WishJpaEntity w where w.memberId = :memberId")
    Page<WishView> findViewsByMemberId(@Param("memberId") UUID memberId, Pageable pageable);

    /**
     * 찜 등록은 idempotent해야 한다(CLAUDE.md 핵심 설계 결정) — 중복 등록·재시도를
     * 에러로 처리하지 않기 위해 유니크 인덱스(uq_wishes_member_project) 대상
     * ON CONFLICT DO NOTHING으로 처리한다. @Modifying 쿼리는 호출부가 트랜잭션
     * 안에 있어야 동작한다(WishService의 @Transactional에 의존).
     *
     * <p>영향 행 수를 반환하는 이유: 이미 찜한 프로젝트에 다시 요청이 와도 0을 돌려주므로,
     * 호출부가 "상태가 실제로 바뀌었을 때만" 아웃박스에 이벤트를 적재할 수 있다.
     */
    @Modifying
    @Query(value = "INSERT INTO wishes (member_id, project_id, created_at) "
            + "VALUES (:memberId, :projectId, now()) "
            + "ON CONFLICT (member_id, project_id) DO NOTHING", nativeQuery = true)
    int insertIgnoringConflict(@Param("memberId") UUID memberId, @Param("projectId") Long projectId);

    /** 찜 해제도 idempotent해야 한다 — 이미 없는 대상 삭제도 정상(영향 행 0)으로 취급. 반환값의 용도는 위와 같다. */
    @Modifying
    @Query(value = "DELETE FROM wishes WHERE member_id = :memberId AND project_id = :projectId", nativeQuery = true)
    int deleteByMemberIdAndProjectId(@Param("memberId") UUID memberId, @Param("projectId") Long projectId);
}
