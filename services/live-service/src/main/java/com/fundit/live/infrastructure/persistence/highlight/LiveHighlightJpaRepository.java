package com.fundit.live.infrastructure.persistence.highlight;

import com.fundit.live.domain.highlight.HighlightKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveHighlightJpaRepository extends JpaRepository<LiveHighlightJpaEntity, Long> {

    List<LiveHighlightJpaEntity> findBySessionIdOrderByStartSecAsc(Long sessionId);

    /** 소비자 화면은 공개된 것만 읽는다. */
    List<LiveHighlightJpaEntity> findBySessionIdAndIsPublicTrueOrderByStartSecAsc(Long sessionId);

    Optional<LiveHighlightJpaEntity> findByPublicId(UUID publicId);

    /**
     * 프로젝트의 공개 클립과 그 방송의 {@code public_id}. 세션 → 프로젝트 조인이라 세션 엔티티를 같이 본다.
     * 결과 행은 {@code [UUID liveId, LiveHighlightJpaEntity]}. 두 엔티티를 select해 count를 자동으로
     * 못 만들므로 {@code countQuery}를 따로 둔다.
     */
    @Query(value = """
            select s.publicId, h from LiveHighlightJpaEntity h, com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity s
            where s.id = h.sessionId
              and s.projectId = :projectId
              and s.status <> com.fundit.live.domain.session.LiveStatus.DRAFT
              and h.kind = com.fundit.live.domain.highlight.HighlightKind.CLIP
              and h.isPublic = true
              and h.generationStatus = com.fundit.live.domain.ai.GenerationStatus.COMPLETED
            order by h.createdAt desc, h.id desc
            """,
            countQuery = """
            select count(h) from LiveHighlightJpaEntity h, com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity s
            where s.id = h.sessionId
              and s.projectId = :projectId
              and s.status <> com.fundit.live.domain.session.LiveStatus.DRAFT
              and h.kind = com.fundit.live.domain.highlight.HighlightKind.CLIP
              and h.isPublic = true
              and h.generationStatus = com.fundit.live.domain.ai.GenerationStatus.COMPLETED
            """)
    Page<Object[]> findPublicClipsByProjectId(@Param("projectId") UUID projectId, Pageable pageable);

    /** 방송 1회당 클립 최대 3개(요구사항정의서 6.6.3) 검증용. 마커는 제한이 없다. */
    long countBySessionIdAndKind(Long sessionId, HighlightKind kind);

    /**
     * <b>노출 수</b>를 올린다 — 공개 목록이 한 번 열릴 때 그 세션의 공개 항목이 전부 +1 된다.
     * "이 클립이 몇 번 재생됐나"가 아니라 "목록에 몇 번 실렸나"다. 클립 단위 재생을 세려면
     * 재생 이벤트를 받을 엔드포인트가 필요한데 아직 없다 — 생기면 그때 컬럼을 나눈다.
     *
     * <p>조회 후 세팅하면 동시 요청에서 갱신이 덮어써지므로 DB에서 한 문장으로 더한다.
     */
    @Modifying
    @Query(value = "UPDATE live_highlights SET view_count = view_count + 1 WHERE session_id = :sessionId "
            + "AND is_public = true", nativeQuery = true)
    int increaseViewCount(@Param("sessionId") Long sessionId);

    @Modifying
    @Query(value = "UPDATE live_highlights SET click_count = click_count + 1 WHERE public_id = :publicId",
            nativeQuery = true)
    int increaseClickCount(@Param("publicId") UUID publicId);
}
