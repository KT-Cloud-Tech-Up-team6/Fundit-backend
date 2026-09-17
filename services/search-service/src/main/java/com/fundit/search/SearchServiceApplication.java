package com.fundit.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling}: 인기 검색어 집계 배치(PopularKeywordAggregationScheduler, SEARCH-015).
 * {@code @EnableAsync}: 검색 로그 적재를 요청 스레드에서 분리(SearchQueryLogEventHandler).
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.fundit")
public class SearchServiceApplication {

    public static void main(String[] arg) {
        SpringApplication.run(SearchServiceApplication.class, arg);
    }
}
