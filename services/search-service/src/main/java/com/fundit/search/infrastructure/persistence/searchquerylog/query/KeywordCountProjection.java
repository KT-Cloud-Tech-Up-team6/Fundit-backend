package com.fundit.search.infrastructure.persistence.searchquerylog.query;

/** SEARCH-015 집계 배치용 — 키워드별 검색 횟수. */
public interface KeywordCountProjection {

    String getKeyword();

    Long getSearchCount();
}
