package com.fundit.order.infrastructure.persistence.funding.query;

/**
 * 라이브 방송 주문 집계 조회 전용 프로젝션(persistence-convention.md §3).
 * 별칭을 큰따옴표로 감싼 이유는 {@code FundingJpaRepository#findLiveOrderStats} 주석 참고.
 */
public interface LiveOrderStatsProjection {

    long getPaidCount();

    long getPaidAmount();

    long getPendingCount();

    long getPendingAmount();
}
