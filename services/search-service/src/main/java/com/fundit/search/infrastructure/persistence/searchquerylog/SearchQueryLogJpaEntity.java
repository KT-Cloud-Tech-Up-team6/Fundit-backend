package com.fundit.search.infrastructure.persistence.searchquerylog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 실행된 모든 검색의 원본 로그 — popular_search_keywords 집계 배치(SEARCH-015)의 소스. */
@Getter
@Entity
@Builder
@Table(name = "search_query_logs")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchQueryLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 비로그인 검색을 허용하므로 NULL 가능(SearchERD.md 5-⑦). */
    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    @Column(name = "result_count", nullable = false)
    private Integer resultCount;

    @Column(name = "searched_at", nullable = false, updatable = false)
    private Instant searchedAt;

    @PrePersist
    protected void onCreate() {
        if (this.searchedAt == null) {
            this.searchedAt = Instant.now();
        }
    }
}
