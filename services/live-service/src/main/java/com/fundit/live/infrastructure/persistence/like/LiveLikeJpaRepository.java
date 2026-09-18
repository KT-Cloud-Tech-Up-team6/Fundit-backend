package com.fundit.live.infrastructure.persistence.like;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * 좋아요는 행의 존재 자체가 상태라 엔티티에 담을 게 없다 —
 * 복합 PK 2컬럼과 created_at뿐이고 상태 전이도 없다(persistence-convention.md 2번).
 * 그래서 JpaEntity 없이 네이티브 쿼리만 둔다.
 */
public interface LiveLikeJpaRepository extends JpaRepository<LiveLikeJpaEntity, LiveLikeId> {

    /**
     * PUT은 idempotent해야 한다 — 같은 요청을 두 번 보내도 결과가 같다.
     * PK가 곧 중복 방지 제약이라 ON CONFLICT DO NOTHING이면 끝난다.
     * 반환값(0=이미 눌림)으로 호출부가 like_count를 올릴지 정한다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO live_likes (session_id, member_id) VALUES (:sessionId, :memberId)
            ON CONFLICT (session_id, member_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnoringConflict(@Param("sessionId") Long sessionId, @Param("memberId") UUID memberId);

    /** 취소도 idempotent다 — 이미 없는 대상 삭제도 정상(영향 행 0)으로 취급한다. */
    @Modifying
    @Query(value = "DELETE FROM live_likes WHERE session_id = :sessionId AND member_id = :memberId",
            nativeQuery = true)
    int deleteByIds(@Param("sessionId") Long sessionId, @Param("memberId") UUID memberId);
}
