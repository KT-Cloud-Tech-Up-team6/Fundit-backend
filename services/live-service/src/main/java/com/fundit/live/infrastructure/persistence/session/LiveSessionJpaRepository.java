package com.fundit.live.infrastructure.persistence.session;

import com.fundit.live.domain.session.LiveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveSessionJpaRepository extends JpaRepository<LiveSessionJpaEntity, Long> {

    /**
     * 본인 소유 세션만 가져온다 — 채널을 조인해 sellerId를 대조한다.
     * 소유권을 세션에 복사해두지 않았으므로 인가 판정이 항상 이 한 쿼리로 끝난다(S4).
     */
    @Query("""
            select s from LiveSessionJpaEntity s
            where s.publicId = :publicId
              and s.channelId in (select c.id from LiveChannelJpaEntity c where c.sellerId = :sellerId)
            """)
    Optional<LiveSessionJpaEntity> findOwned(@Param("publicId") UUID publicId, @Param("sellerId") UUID sellerId);

    @Query("""
            select s from LiveSessionJpaEntity s
            where s.channelId in (select c.id from LiveChannelJpaEntity c where c.sellerId = :sellerId)
              and (:status is null or s.status = :status)
            order by s.createdAt desc, s.id desc
            """)
    Page<LiveSessionJpaEntity> findMine(@Param("sellerId") UUID sellerId,
                                        @Param("status") LiveStatus status,
                                        Pageable pageable);

    /**
     * 소비자 목록. <b>DRAFT는 절대 포함하지 않는다</b> — 설정이 끝나지 않은 방송이다.
     * 상태 필터를 생략해도 DRAFT가 새지 않도록 제외 조건을 쿼리에 고정한다.
     */
    @Query("""
            select s from LiveSessionJpaEntity s
            where s.status <> com.fundit.live.domain.session.LiveStatus.DRAFT
              and (:status is null or s.status = :status)
            """)
    Page<LiveSessionJpaEntity> findPublic(@Param("status") LiveStatus status, Pageable pageable);

    List<LiveSessionJpaEntity> findByStatusOrderByActualStartAtDesc(LiveStatus status);
}
