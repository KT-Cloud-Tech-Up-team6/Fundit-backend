package com.fundit.payment.presentation.controller;

import com.fundit.payment.application.refund.RefundQueryService;
import com.fundit.payment.presentation.dto.InternalRefundStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * order-service 펀딩 내역(V03/V06) 연동 내부 API. 내부 전용 보호는
 * {@code infrastructure.security.InternalEndpointConfig}에 등록한 InternalEndpoint 빈이 담당한다
 * (fulfillment-service {@code InternalFulfillmentController}와 동일 패턴).
 */
@RestController
@RequiredArgsConstructor
public class InternalRefundController {

    private final RefundQueryService refundQueryService;

    /**
     * 주문별 취소·반품·교환 신청 이력(최신순) 배치 조회 — 펀딩 내역 카드의 버튼이 신청 후
     * "취소 내역"/"반품·교환 내역"으로 바뀌는 데 쓴다. 건별 호출(N+1)을 막으려고 배치만 둔다.
     */
    @GetMapping("/internal/refunds/statuses")
    public List<InternalRefundStatusResponse> getStatuses(@RequestParam List<UUID> fundingIds) {
        return refundQueryService.listByFundingIds(fundingIds).stream()
                .map(InternalRefundStatusResponse::from)
                .toList();
    }
}
