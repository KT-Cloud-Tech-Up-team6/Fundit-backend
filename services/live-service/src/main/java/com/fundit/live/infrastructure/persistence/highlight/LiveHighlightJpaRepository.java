package com.fundit.live.infrastructure.persistence.highlight;

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

    /** 방송 1회당 클립 최대 3개(요구사항정의서 6.6.3) 검증용. 마커는 제한이 없다. */
    long countBySessionIdAndKind(Long sessionId, String kind);

    /**
     * 조회·클릭 카운터. 조회 후 세팅하면 동시 요청에서 갱신이 덮어써진다 —
     * DB에서 한 문장으로 더한다.
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
