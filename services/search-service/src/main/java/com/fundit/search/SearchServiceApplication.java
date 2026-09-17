package com.fundit.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @EnableScheduling: 인기 검색어 집계 배치(PopularKeywordAggregationScheduler, SEARCH-015)가 필요로 한다. */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.fundit")
public class SearchServiceApplication {

    public static void main(String[] arg) {
        SpringApplication.run(SearchServiceApplication.class, arg);
    }
}
