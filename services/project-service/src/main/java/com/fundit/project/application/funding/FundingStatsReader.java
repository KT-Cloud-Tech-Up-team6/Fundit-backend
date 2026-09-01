package com.fundit.project.application.funding;

import com.fundit.project.application.port.FundingPort;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * order-service 집계값 조회를 캐시로 감싼다.
 * <p>
 * 구매자 상세조회(PROJECT-003)와 판매자 펀딩현황(PROJECT-027)이 같은 최신성을 보도록
 * 두 경로 모두 이 컴포넌트를 거친다 — 한쪽만 실시간으로 만들지 않는다.
 * TTL은 CacheConfig에서 5분으로 지정한다.
 */
@Component
@RequiredArgsConstructor
public class FundingStatsReader {

    public static final String CACHE_NAME = "fundingStats";

    private final FundingPort fundingPort;

    @Cacheable(cacheNames = CACHE_NAME, key = "#projectId")
    public FundingPort.FundingStats read(Long projectId) {
        return fundingPort.findStats(projectId);
    }
}
