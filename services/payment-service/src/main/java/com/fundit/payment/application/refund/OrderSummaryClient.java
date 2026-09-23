package com.fundit.payment.application.refund;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PAYMENT-003 환불 목록(V04)용 — order-service 내부 API(`GET /internal/orders/order-summaries`)를
 * 배치로 조회해 프로젝트명·상품/옵션·수량을 채운다. 부가 정보라 조회 실패해도 목록 자체는
 * 내려가야 해서 예외를 던지지 않고 빈 맵으로 degrade한다({@code OrderFundingClient}와 달리
 * PAYMENT-001 핵심 흐름을 막지 않는다).
 */
public interface OrderSummaryClient {

    Map<UUID, OrderSummary> fetchBatch(List<UUID> orderIds);

    record OrderSummary(String projectTitle, List<LineItem> lineItems) {
    }

    record LineItem(String rewardName, int quantity, long unitPrice, List<LineItemOption> options) {
    }

    /** 주문 시점 옵션 스냅샷(order-service {@code funding_line_item_options}) — 옵션 수정과 무관하게 불변. */
    record LineItemOption(String optionGroupName, String optionValue) {
    }
}
