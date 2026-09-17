package com.fundit.search.application.search;

import com.fundit.search.infrastructure.persistence.popularkeyword.PopularSearchKeywordJpaEntity;
import com.fundit.search.infrastructure.persistence.popularkeyword.PopularSearchKeywordJpaRepository;
import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * SEARCH-015. 최근 검색 로그를 집계해 인기 검색어 상위 N개 스냅샷을 갱신한다. 집계 대상 로그가
 * 0건이면 빈 스냅샷으로 초기화한다(이전 랭킹 유지 아님 — SearchDomainFunctionalSpec.md SEARCH-015 참고).
 *
 * <p>집계 윈도우(24시간)·갱신 주기(1시간)·노출 개수(10개)는 전부 PM 확인 전 기본값이다(SearchERD.md 5-⑧).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PopularKeywordAggregationScheduler {

    private static final int AGGREGATION_WINDOW_HOURS = 24;
    private static final int TOP_N = 10;

    private final SearchQueryLogJpaRepository searchQueryLogJpaRepository;
    private final PopularSearchKeywordJpaRepository popularSearchKeywordJpaRepository;

    @Scheduled(fixedDelayString = "${search.batch.popular-keyword.interval-ms:3600000}")
    @Transactional
    public void run() {
        var since = Instant.now().minus(AGGREGATION_WINDOW_HOURS, ChronoUnit.HOURS);
        var topKeywords = searchQueryLogJpaRepository.aggregateTopKeywords(since, TOP_N);

        popularSearchKeywordJpaRepository.deleteAllInBatch();
        var aggregatedAt = Instant.now();
        List<PopularSearchKeywordJpaEntity> ranked = new ArrayList<>();
        for (int i = 0; i < topKeywords.size(); i++) {
            var row = topKeywords.get(i);
            ranked.add(PopularSearchKeywordJpaEntity.builder()
                    .rank(i + 1)
                    .keyword(row.getKeyword())
                    .searchCount(row.getSearchCount().intValue())
                    .aggregatedAt(aggregatedAt)
                    .build());
        }
        popularSearchKeywordJpaRepository.saveAll(ranked);
        log.info("인기 검색어 {}건을 갱신했습니다.", ranked.size());
    }
}
