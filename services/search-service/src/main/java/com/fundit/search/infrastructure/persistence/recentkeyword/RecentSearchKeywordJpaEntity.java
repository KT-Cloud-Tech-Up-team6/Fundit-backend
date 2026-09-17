package com.fundit.search.infrastructure.persistence.recentkeyword;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 회원별 최근 검색어(SEARCH-008/009). 같은 키워드 재검색은 upsert로 searched_at만 갱신한다(멱등) —
 * 단순 애그리거트(persistence-convention.md §2), upsert/삭제는 Repository의 @Modifying 쿼리로 처리한다.
 */
@Getter
@Entity
@Builder
@Table(name = "recent_search_keywords")
@IdClass(RecentSearchKeywordId.class)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecentSearchKeywordJpaEntity {

    @Id
    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Id
    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    @Column(name = "searched_at", nullable = false)
    private Instant searchedAt;
}
