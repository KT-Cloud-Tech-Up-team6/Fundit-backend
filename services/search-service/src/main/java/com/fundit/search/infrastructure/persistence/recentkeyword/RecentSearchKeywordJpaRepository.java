package com.fundit.search.infrastructure.persistence.recentkeyword;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RecentSearchKeywordJpaRepository extends JpaRepository<RecentSearchKeywordJpaEntity, RecentSearchKeywordId> {

    /** SEARCH-009. 본인 최근 검색어만 searched_at 내림차순으로 조회한다(security.md S4). */
    List<RecentSearchKeywordJpaEntity> findByMemberIdOrderBySearchedAtDesc(UUID memberId, Pageable pageable);

    /** SEARCH-009 개별 삭제. 존재하지 않는 키워드 삭제도 정상(영향 행 0)으로 취급한다 — idempotent. */
    void deleteByMemberIdAndKeyword(UUID memberId, String keyword);

    /** SEARCH-009 전체 삭제. */
    void deleteByMemberId(UUID memberId);

    /** SEARCH-008. 같은 키워드 재검색은 searched_at만 최신화한다(멱등). */
    @Modifying
    @Query(value = "INSERT INTO recent_search_keywords (member_id, keyword, searched_at) "
            + "VALUES (:memberId, :keyword, now()) "
            + "ON CONFLICT (member_id, keyword) DO UPDATE SET searched_at = now()", nativeQuery = true)
    void upsert(@Param("memberId") UUID memberId, @Param("keyword") String keyword);

    /** SEARCH-008. member_id당 보관 개수(정책값, 기본 10개 — SearchERD.md 5-⑧)를 초과하는 오래된 행을 지운다. */
    @Modifying
    @Query(value = "DELETE FROM recent_search_keywords WHERE member_id = :memberId AND keyword NOT IN ("
            + "SELECT keyword FROM recent_search_keywords WHERE member_id = :memberId "
            + "ORDER BY searched_at DESC LIMIT :limit)", nativeQuery = true)
    void deleteExceedingLimit(@Param("memberId") UUID memberId, @Param("limit") int limit);
}
