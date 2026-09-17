package com.fundit.search.infrastructure.persistence.searchquerylog;

import com.fundit.search.infrastructure.persistence.searchquerylog.query.KeywordCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SearchQueryLogJpaRepository extends JpaRepository<SearchQueryLogJpaEntity, Long> {

    /** SEARCH-015. 집계 윈도우·상위 개수는 정책값(기본 24시간/10개, SearchERD.md 5-⑧)이라 호출부에서 넘겨받는다. */
    @Query(value = "SELECT keyword AS keyword, COUNT(*) AS search_count FROM search_query_logs "
            + "WHERE searched_at > :since GROUP BY keyword ORDER BY COUNT(*) DESC LIMIT :limit", nativeQuery = true)
    List<KeywordCountProjection> aggregateTopKeywords(@Param("since") Instant since, @Param("limit") int limit);
}
