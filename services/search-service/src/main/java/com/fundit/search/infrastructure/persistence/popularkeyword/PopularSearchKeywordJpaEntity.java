package com.fundit.search.infrastructure.persistence.popularkeyword;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 전역 인기 검색어 "현재 스냅샷"(SEARCH-010/015). 이력을 보관하지 않고 배치가 매 주기
 * TRUNCATE 후 재적재하는 단순 애그리거트다(persistence-convention.md §2).
 */
@Getter
@Entity
@Builder
@Table(name = "popular_search_keywords")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PopularSearchKeywordJpaEntity {

    @Id
    @Column(name = "rank")
    private Integer rank;

    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    @Column(name = "search_count", nullable = false)
    private Integer searchCount;

    @Column(name = "aggregated_at", nullable = false)
    private Instant aggregatedAt;
}
