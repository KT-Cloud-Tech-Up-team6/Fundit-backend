package com.fundit.live.infrastructure.persistence.session;

import com.fundit.live.domain.session.LiveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 시작·종료 전용 잠금 조회. 같은 방송에 시작 요청이 동시에 들어오면(버튼 더블클릭 등)
     * 둘 다 상태 검사를 통과해 <b>IVS 채팅방이 두 개 생기고</b> 종료도 두 번 발행된다.
     * 행 잠금으로 한 요청만 통과시킨다 — 조회 경로(목록·설정)는 잠그지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from LiveSessionJpaEntity s
            where s.publicId = :publicId
              and s.channelId in (select c.id from LiveChannelJpaEntity c where c.sellerId = :sellerId)
            """)
    Optional<LiveSessionJpaEntity> findOwnedForUpdate(@Param("publicId") UUID publicId,
                                                      @Param("sellerId") UUID sellerId);

    /** 위와 같은 이유의 잠금인데 호출자가 AI 서버라 대조할 sellerId가 없다(내부 콜백 전용). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LiveSessionJpaEntity s where s.publicId = :publicId")
    Optional<LiveSessionJpaEntity> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

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
            order by s.createdAt desc, s.id desc
            """)
    Page<LiveSessionJpaEntity> findPublic(@Param("status") LiveStatus status, Pageable pageable);

    /**
     * "팔로우한 창작자" 필터. {@code sellerIds}가 빈 컬렉션이면 안 부른다 — JPQL {@code IN}은
     * 바인딩 파라미터가 null이면 컬렉션 파라미터 확장이 실패한다(스칼라 {@code = :x}와 다른
     * 함정). 그래서 필터가 없을 때 쓰는 {@link #findPublic(LiveStatus, Pageable)}와 메서드를
     * 분리했다 — 하나로 합쳐 {@code :sellerIds is null}로 우회하지 않는다.
     */
    @Query("""
            select s from LiveSessionJpaEntity s
            where s.status <> com.fundit.live.domain.session.LiveStatus.DRAFT
              and (:status is null or s.status = :status)
              and s.channelId in (select c.id from LiveChannelJpaEntity c where c.sellerId in :sellerIds)
            order by s.createdAt desc, s.id desc
            """)
    Page<LiveSessionJpaEntity> findPublicBySellerIds(@Param("status") LiveStatus status,
                                                      @Param("sellerIds") List<UUID> sellerIds, Pageable pageable);

    /**
     * 공개 단건 조회(시청 정보·VOD·채팅 토큰). <b>DRAFT는 여기서 걸러 404가 되게 한다</b> —
     * 호출부마다 {@code if (DRAFT)}를 붙이면 네 번째 호출부에서 빠진다. 실제로 세 곳 중 한 곳에만
     * 있어서, 설정 중인 방송에 요청을 넣으면 409가 돌아와 존재가 드러났다(security.md S10).
     */
    @Query("""
            select s from LiveSessionJpaEntity s
            where s.publicId = :publicId
              and s.status <> com.fundit.live.domain.session.LiveStatus.DRAFT
            """)
    Optional<LiveSessionJpaEntity> findPublicByPublicId(@Param("publicId") UUID publicId);

    List<LiveSessionJpaEntity> findByStatusOrderByActualStartAtDesc(LiveStatus status);

    /** 소유권 검증이 필요 없는 공개 조회(시청 정보·VOD·채팅 토큰). */
    Optional<LiveSessionJpaEntity> findByPublicId(UUID publicId);

    /** 채팅 적재에서 룸 ARN → 세션 변환. ARN이 세션 컬럼이라 조인 없이 단일 조회다. */
    Optional<LiveSessionJpaEntity> findByIvsChatRoomArn(String ivsChatRoomArn);

    /**
     * like_count 증감. 조회 후 세팅하면 동시 요청에서 갱신이 덮어써져 카운트가 어긋난다 —
     * DB에서 한 문장으로 더한다. 0 미만으로 내려가지 않게 조건을 건다.
     */
    @Modifying
    @Query(value = "UPDATE live_sessions SET like_count = like_count + :delta "
            + "WHERE id = :sessionId AND like_count + :delta >= 0", nativeQuery = true)
    int addLikeCount(@Param("sessionId") Long sessionId, @Param("delta") int delta);
}
