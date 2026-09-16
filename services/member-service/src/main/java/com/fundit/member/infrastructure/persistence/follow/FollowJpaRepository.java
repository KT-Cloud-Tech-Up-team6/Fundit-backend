package com.fundit.member.infrastructure.persistence.follow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface FollowJpaRepository extends JpaRepository<FollowJpaEntity, FollowId> {

    /**
     * 팔로우는 idempotent해야 한다 — 하트 버튼은 더블탭·재시도가 일상이라 중복 요청을 409로
     * 돌려주면 프론트가 "이미 팔로우함"을 성공으로 바꾸는 분기를 또 짜야 한다(찜 등록과 동일 원칙).
     *
     * <p>"조회 후 없으면 INSERT"를 쓰지 않는 이유: 동시 요청 둘이 같이 "없음"을 보고 둘 다
     * INSERT하면 PK 위반으로 하나가 500이 된다. 판정을 DB에 맡기면 경합이 사라진다.
     *
     * <p>@Modifying 쿼리는 호출부가 트랜잭션 안에 있어야 동작한다(FollowService의 @Transactional에 의존).
     */
    @Modifying
    @Query(value = "INSERT INTO follows (member_id, seller_id, created_at) "
            + "VALUES (:memberId, :sellerId, now()) "
            + "ON CONFLICT (member_id, seller_id) DO NOTHING", nativeQuery = true)
    void insertIgnoringConflict(@Param("memberId") UUID memberId, @Param("sellerId") UUID sellerId);

    /** 언팔로우도 idempotent — 이미 없는 대상 삭제도 정상(영향 행 0)으로 취급. */
    @Modifying
    @Query(value = "DELETE FROM follows WHERE member_id = :memberId AND seller_id = :sellerId",
            nativeQuery = true)
    void deleteByMemberIdAndSellerId(@Param("memberId") UUID memberId, @Param("sellerId") UUID sellerId);

    /**
     * 판매자 이름은 스냅샷이 아니라 조인으로 가져온다 — 같은 DB라 복사할 이유가 없다.
     * 탈퇴한 판매자는 목록에서 제외한다(members.deleted_at).
     *
     * <p>createdAt만으로 정렬하지 않는 이유: insertIgnoringConflict가 Postgres now()
     * (=트랜잭션 시작 시각)를 쓰므로 동률이 실제로 생긴다. 동률이면 페이지마다 순서가 달라져
     * 같은 행이 두 페이지에 나오거나 아예 빠진다. sellerId를 2차 키로 둬 순서를 고정한다.
     */
    @Query("""
            select new com.fundit.member.infrastructure.persistence.follow.FollowView(
                       f.sellerId, m.name, m.nickname, f.createdAt)
            from FollowJpaEntity f
            join MemberJpaEntity m on m.id = f.sellerId
            where f.memberId = :memberId and m.deletedAt is null
            order by f.createdAt desc, f.sellerId desc
            """)
    Page<FollowView> findViewsByMemberId(@Param("memberId") UUID memberId, Pageable pageable);
}
