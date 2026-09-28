package com.fundit.order.infrastructure.persistence.funding;

import com.fundit.order.infrastructure.persistence.funding.query.LiveOrderStatsProjection;
import com.fundit.order.infrastructure.persistence.funding.query.SupporterActivityProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundingJpaRepository extends JpaRepository<FundingJpaEntity, Long> {

    Optional<FundingJpaEntity> findByPublicId(UUID publicId);

    /** ORDER-003 멱등 키 조회 — 회원 범위로 유니크(uq_fundings_member_idempotency_key). */
    Optional<FundingJpaEntity> findByMemberIdAndIdempotencyKey(UUID memberId, String idempotencyKey);

    /** payment-service 환불 목록(V04) 배치 조회용. */
    List<FundingJpaEntity> findByPublicIdIn(List<UUID> publicIds);

    /**
     * PROJECT-015 펀딩 집계 배치 대상 — 참여자로 셀 수 있는(결제완료, 미환불) 펀딩이 하나라도
     * 있는 프로젝트만 순회한다. PENDING(미결제)·취소·환불 건은 통계에서 제외한다.
     *
     * <p><b>레거시 {@code project_id}(Long)가 아니라 {@code project_public_id}(UUID)로 조회한다.</b>
     * cross-service ID 통일(#69) 이후 {@link com.fundit.order.infrastructure.persistence.funding.FundingMapper}가
     * 신규 펀딩에 레거시 컬럼을 더 이상 채우지 않아, 그 컬럼으로 조회하면 항상 빈 목록이 나와
     * 이 배치가 실질적으로 아무 것도 처리하지 못한다.
     */
    @Query(value = "SELECT DISTINCT project_public_id FROM fundings "
            + "WHERE status IN ('FUNDING_IN_PROGRESS','GOAL_ACHIEVED') AND project_public_id IS NOT NULL",
            nativeQuery = true)
    List<UUID> findDistinctProjectPublicIdsWithCountableFundings();

    Page<FundingJpaEntity> findByMemberId(UUID memberId, Pageable pageable);

    Page<FundingJpaEntity> findByMemberIdAndStatus(UUID memberId, String status, Pageable pageable);

    List<FundingJpaEntity> findByStatusAndPaymentExpiresAtBefore(String status, Instant threshold);

    /** ORDER-006/내부 API — project-service publicId(UUID) 기준. */
    List<FundingJpaEntity> findByProjectPublicIdAndStatusIn(UUID projectPublicId, List<String> statuses);

    /**
     * ORDER-001 — 취소/만료된 참여를 제외한 서포터 활동 목록(최신순). 금액은 쿠폰 할인 반영 전
     * 리워드 합산액이다[가정 — 활동 피드는 정확한 최종 결제액보다 참여 시점 스냅샷이면 충분].
     */
    @Query("select f.memberId as memberId, f.createdAt as createdAt, "
            + "coalesce(sum(li.unitPrice * li.quantity), 0) as amount "
            + "from FundingJpaEntity f join FundingLineItemJpaEntity li on li.fundingId = f.id "
            + "where f.projectPublicId = :projectId and f.status not in ('CANCELLED_BY_MEMBER', 'PAYMENT_EXPIRED') "
            + "group by f.memberId, f.createdAt "
            + "order by f.createdAt desc")
    Page<SupporterActivityProjection> findSupporterActivity(@Param("projectId") UUID projectId, Pageable pageable);

    /**
     * 방송 중 화면의 주문 건수·매출 집계. 방송 중 3~5초 폴링이 걸리는 경로라 쿼리는 <b>1개</b>로
     * 끝낸다 — 결제완료/미결제를 {@code FILTER}로 한 번에 센다.
     *
     * <p>금액은 쿠폰 할인 반영 전 리워드 합산액이다({@link #findSupporterActivity}와 같은 기준,
     * 배송비 제외). 취소(CANCELLED_BY_MEMBER)·만료(PAYMENT_EXPIRED)·환불 건은 두 FILTER 어디에도
     * 걸리지 않아 자동으로 빠진다.
     *
     * <p>{@code count(DISTINCT f.id)}인 이유: 주문 1건에 리워드가 여러 줄이면 조인 후 행이 늘어나
     * 그냥 {@code count(*)}로 세면 리워드 줄 수를 주문 건수로 내보내게 된다. 리워드가 없는 주문도
     * 건수에선 빠지지 않도록 LEFT JOIN을 쓴다.
     *
     * <p>별칭을 {@code "paidCount"}처럼 큰따옴표로 감싼 이유: 따옴표가 없으면 PostgreSQL이 컬럼
     * 라벨을 소문자로 내려서(paidcount) 인터페이스 프로젝션이 매핑할 속성을 찾지 못한다.
     */
    @Query(value = """
            SELECT count(DISTINCT f.id) FILTER (WHERE f.status IN ('FUNDING_IN_PROGRESS','GOAL_ACHIEVED')) AS "paidCount",
                   coalesce(sum(li.unit_price * li.quantity)
                            FILTER (WHERE f.status IN ('FUNDING_IN_PROGRESS','GOAL_ACHIEVED')), 0) AS "paidAmount",
                   count(DISTINCT f.id) FILTER (WHERE f.status = 'PENDING') AS "pendingCount",
                   coalesce(sum(li.unit_price * li.quantity)
                            FILTER (WHERE f.status = 'PENDING'), 0) AS "pendingAmount"
            FROM fundings f
            LEFT JOIN funding_line_items li ON li.funding_id = f.id
            WHERE f.live_session_id = :liveSessionId
            """, nativeQuery = true)
    LiveOrderStatsProjection findLiveOrderStats(@Param("liveSessionId") Long liveSessionId);

    /** cross-service ID 통일(#69) 백필 대상 — 레거시 Long project_id는 있지만 UUID가 아직 안 채워진 행. */
    List<FundingJpaEntity> findByProjectIdIsNotNullAndProjectPublicIdIsNull();

    @Modifying
    @Query("update FundingJpaEntity f set f.projectPublicId = :projectPublicId where f.id = :id")
    void updateProjectPublicId(@Param("id") Long id, @Param("projectPublicId") UUID projectPublicId);

    /**
     * #129 — 판매자 발송목록. {@code shipping_address}가 JSONB(Hibernate JSON 타입 매핑)라
     * {@code ->>'recipientName'} 연산자를 쓰려면 네이티브 쿼리가 필요하다(JPQL로는 불가).
     *
     * <p>{@code :q}를 {@code CAST(:q AS text)}로 명시적으로 캐스팅한다 — 캐스팅 없이
     * {@code concat('%', :q, '%')}에 null을 바인딩하면 PostgreSQL이 플래닝 시점에 파라미터 타입을
     * bytea로 잘못 추론해 {@code lower(bytea) does not exist}로 500이 난다(프로젝트 목록 조회
     * 버그(bug/project-inquire#127)와 동일한 함정). 그때는 쿼리 메서드를 분리해서 피했지만, 여기는
     * 파라미터 자체를 캐스팅해 애초에 타입 추론이 필요 없게 만드는 방식(2안)으로 막는다 — 네이티브
     * SQL이라 HQL cast 지원 범위 문제가 없어 이 방식이 안전하다.
     *
     * <p>{@code shippingFilter}는 서비스 계층이 항상 "ALL"/"WAITING"/"SHIPPED" 중 하나로 채워
     * 넘긴다(null 없음) — 그래서 이 파라미터는 같은 함정을 겪지 않는다.
     *
     * <p>{@code q}는 호출부({@link FundingPersistenceAdapter})가 LIKE 와일드카드(`%`/`_`)를
     * 리터럴로 이스케이프해서 넘긴다 — 그래서 `ESCAPE '\'`로 그 이스케이프를 해석하도록 명시한다.
     * 이스케이프 없이 그대로 쓰면 검색어에 `%`/`_`가 포함될 때 의도한 부분일치 대신 와일드카드로
     * 해석돼 엉뚱한 행까지 매칭된다.
     */
    @Query(value = """
            SELECT * FROM fundings f
            WHERE f.project_public_id = :projectId
              AND f.status = 'GOAL_ACHIEVED'
              AND (:shippingFilter = 'ALL'
                   OR (:shippingFilter = 'WAITING' AND f.shipped_at IS NULL)
                   OR (:shippingFilter = 'SHIPPED' AND f.shipped_at IS NOT NULL))
              AND (CAST(:q AS text) IS NULL
                   OR lower(f.shipping_address ->> 'recipientName') LIKE lower(concat('%', CAST(:q AS text), '%')) ESCAPE '\\'
                   OR CAST(f.public_id AS text) LIKE concat('%', CAST(:q AS text), '%') ESCAPE '\\')
            ORDER BY f.created_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM fundings f
            WHERE f.project_public_id = :projectId
              AND f.status = 'GOAL_ACHIEVED'
              AND (:shippingFilter = 'ALL'
                   OR (:shippingFilter = 'WAITING' AND f.shipped_at IS NULL)
                   OR (:shippingFilter = 'SHIPPED' AND f.shipped_at IS NOT NULL))
              AND (CAST(:q AS text) IS NULL
                   OR lower(f.shipping_address ->> 'recipientName') LIKE lower(concat('%', CAST(:q AS text), '%')) ESCAPE '\\'
                   OR CAST(f.public_id AS text) LIKE concat('%', CAST(:q AS text), '%') ESCAPE '\\')
            """,
            nativeQuery = true)
    Page<FundingJpaEntity> findSellerOrders(@Param("projectId") UUID projectId,
                                             @Param("shippingFilter") String shippingFilter,
                                             @Param("q") String q, Pageable pageable);

    /** #129 — 판매자 발송목록 탭 건수. */
    long countByProjectPublicIdAndStatusAndShippedAtIsNull(UUID projectPublicId, String status);

    /** #129 — 판매자 발송목록 탭 건수. */
    long countByProjectPublicIdAndStatusAndShippedAtIsNotNull(UUID projectPublicId, String status);

    /**
     * #129 — fulfillment-service {@code shipment.shipped.v1} 구독 처리. 조건부 UPDATE라
     * 중복 수신(Kafka at-least-once)에도 두 번째부터는 갱신 행이 0건이라 idempotent하다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update FundingJpaEntity f set f.shippedAt = :shippedAt where f.publicId = :fundingId and f.shippedAt is null")
    void markShippedIfAbsent(@Param("fundingId") UUID fundingId, @Param("shippedAt") Instant shippedAt);
}
