package com.fundit.fulfillment.application.funding;

import java.util.UUID;

/**
 * order-service가 아웃박스로 발행하는 펀딩 성립 이벤트를 처리하는 인바운드 포트(FULFILLMENT-001).
 * 이벤트 필드는 order-service {@code funding.succeeded.v1}와 동일한 계약이다.
 * {@code projectPublicId}/{@code orderId}는 뒤에 추가된 필드라 Jackson이 구버전 payload에도 바인딩한다.
 */
public interface FundingSuccessEventListener {

    void onFundingSucceeded(FundingSucceededEvent event);

    record FundingSucceededEvent(Long fundingId, Long projectId, UUID projectPublicId, UUID orderId) {
    }
}
