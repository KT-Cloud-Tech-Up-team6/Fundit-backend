package com.fundit.live.infrastructure.persistence.session;

import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.session.query.LiveStatusCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
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

    /**
     * order-service 주문 생성 시 "이 프로젝트가 지금 방송 중인가"(내부 전용). 판매자당 채널이 1개라
     * 프로젝트당 동시에 LIVE인 세션은 최대 1개다 — {@code findFirst}는 방어용이다.
     */
    Optional<LiveSessionJpaEntity> findFirstByProjectIdAndStatus(UUID projectId, LiveStatus status);

    /** 위와 같은 이유의 잠금인데 호출자가 AI 서버라 대조할 sellerId가 없다(내부 콜백 전용). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LiveSessionJpaEntity s where s.publicId = :publicId")
    Optional<LiveSessionJpaEntity> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    /**
     * {@code statuses}는 항상 non-null·non-empty로 넘겨야 한다({@link com.fundit.live.application.session.LiveQueryService}가
     * 필터 미지정 시 전체 상태 목록으로 채워 넘긴다) — JPQL {@code in}은 컬렉션 파라미터가
     * null이면 바인딩 자체가 실패한다({@link #findPublicBySellerIds} 주석과 같은 함정).
     */
    @Query("""
            select s from LiveSessionJpaEntity s
            where s.channelId in (select c.id from LiveChannelJpaEntity c where c.sellerId = :sellerId)
              and s.status in :statuses
              and (:projectId is null or s.projectId = :projectId)
              and (:q is null or lower(s.introText) like lower(concat('%', cast(:q as string), '%')))
            order by s.createdAt desc, s.id desc
            """)
    Page<LiveSessionJpaEntity> findMine(@Param("sellerId") UUID sellerId,
                                        @Param("statuses") List<LiveStatus> statuses,
                                        @Param("projectId") UUID projectId,
                                        @Param("q") String q,
                                        Pageable pageable);

    /** 스튜디오 상태 탭 배지용 건수 집계(FE 요청). 그룹핑 없이 {@link LiveStatus} 5종 그대로 낸다. */
    @Query("""
            select s.status as status, count(s) as count
            from LiveSessionJpaEntity s
            where s.channelId in (select c.id from LiveChannelJpaEntity c where c.sellerId = :sellerId)
            group by s.status
            """)
    List<LiveStatusCountProjection> countBySellerIdGroupByStatus(@Param("sellerId") UUID sellerId);

    /**
     * 소비자 목록. <b>DRAFT는 절대 포함하지 않는다</b> — 설정이 끝나지 않은 방송이다.
     * 상태 필터를 생략해도 DRAFT가 새지 않도록 제외 조건을 쿼리에 고정한다.
     */
    @Query("""
            select s from LiveSessionJpaEntity s
            where s.status <> com.fundit.live.domain.session.LiveStatus.DRAFT
              and (:status is null or s.status = :status)
              and (:projectId is null or s.projectId = :projectId)
            order by s.createdAt desc, s.id desc
            """)
    Page<LiveSessionJpaEntity> findPublic(@Param("status") LiveStatus status, @Param("projectId") UUID projectId,
                                          Pageable pageable);

    /**
     * "팔로우한 창작자" 필터. {@code sellerIds}가 빈 컬렉션이면 안 부른다 — JPQL {@code IN}은
     * 바인딩 파라미터가 null이면 컬렉션 파라미터 확장이 실패한다(스칼라 {@code = :x}와 다른
     * 함정). 그래서 필터가 없을 때 쓰는 {@link #findPublic(LiveStatus, UUID, Pageable)}와 메서드를
     * 분리했다 — 하나로 합쳐 {@code :sellerIds is null}로 우회하지 않는다.
     */
    @Query("""
            select s from LiveSessionJpaEntity s
            where s.status <> com.fundit.live.domain.session.LiveStatus.DRAFT
              and (:status is null or s.status = :status)
              and (:projectId is null or s.projectId = :projectId)
              and s.channelId in (select c.id from LiveChannelJpaEntity c where c.sellerId in :sellerIds)
            order by s.createdAt desc, s.id desc
            """)
    Page<LiveSessionJpaEntity> findPublicBySellerIds(@Param("status") LiveStatus status,
                                                      @Param("projectId") UUID projectId,
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

    /** dev 썸네일 보완 대상(#228) — 썸네일 입력 경로가 생기기 전에 만든 LIVE. */
    List<LiveSessionJpaEntity> findByThumbnailUrlIsNull();

    /**
     * dev 목업 시더·썸네일 보완 전용 — 썸네일이 <b>비어 있을 때만</b> 채운다. 판매자가 설정한 썸네일은 덮지 않아 재기동해도
     * 결과가 같다. 도메인 {@code updateSettings}는 LIVE 상태면 409라(목업 절반이 LIVE) 조건부 UPDATE로 한다.
     * 시더에 트랜잭션이 없어 메서드가 직접 연다.
     *
     * @return 채운 행 수(0 또는 1)
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update LiveSessionJpaEntity s set s.thumbnailUrl = :url where s.publicId = :publicId and s.thumbnailUrl is null")
    int fillThumbnailIfAbsent(@Param("publicId") UUID publicId, @Param("url") String url);

    /**
     * 녹화 완료 시 다시보기 URL을 채운다(#222, #232). 대상은 그 채널에서 <b>녹화 구간({@code from}~{@code to})
     * 안에 실제로 방송을 시작한 가장 이른 1건</b>이다. 판매자가 OBS를 켜 둔 채 방송을 이어 하면 녹화 하나에
     * 여러 방송이 담기는데, 녹화는 앞 방송부터 시작하므로 앞 방송에 붙인다(뒤 방송은 다시보기 없음 — 한계).
     *
     * <p>후보는 vod 유무와 상관없이 고르고, 이미 값이 있으면 건드리지 않는다 — vod가 빈 방송만 후보로 삼으면
     * 같은 이벤트가 재전송될 때 뒤 방송에 같은 URL이 붙는다. 엔티티 저장({@code applyFrom})은 vod 컬럼을
     * 일부러 안 쓰므로 이 경로로만 쓴다. 세션에 IVS stream id가 없어 채널+시각으로 찾는다.
     *
     * @return 채운 행 수(0 또는 1)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE live_sessions SET vod_url = :url, vod_ready_at = :to
             WHERE id = (SELECT id FROM live_sessions
                          WHERE channel_id = :channelId AND actual_start_at BETWEEN :from AND :to
                          ORDER BY actual_start_at ASC LIMIT 1)
               AND vod_url IS NULL
            """, nativeQuery = true)
    int fillVodIfAbsent(@Param("channelId") Long channelId, @Param("url") String url,
                        @Param("from") Instant from, @Param("to") Instant to);

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

    /**
     * {@code addLikeCount} 직후 실제 값을 다시 읽는다 — 영속성 컨텍스트에 이미 로드된
     * 엔티티의 {@code likeCount} 필드는 벌크 UPDATE를 반영하지 못해 stale하다. 스칼라 조회라
     * 1차 캐시를 안 거치므로 항상 최신값이다.
     */
    @Query(value = "SELECT like_count FROM live_sessions WHERE id = :sessionId", nativeQuery = true)
    int findLikeCount(@Param("sessionId") Long sessionId);
}
