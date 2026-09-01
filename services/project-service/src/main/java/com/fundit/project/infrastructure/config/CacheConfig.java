package com.fundit.project.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.fundit.project.application.funding.FundingStatsReader;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 펀딩 집계 캐시. 갱신 주기는 구매자 상세조회와 판매자 펀딩현황 모두 5분으로 통일한다
 * (services/project-service/CLAUDE.md 핵심 설계 결정).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration FUNDING_STATS_TTL = Duration.ofMinutes(5);

    @Bean
    public CaffeineCacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(FundingStatsReader.CACHE_NAME);
        cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(FUNDING_STATS_TTL));
        return cacheManager;
    }
}
