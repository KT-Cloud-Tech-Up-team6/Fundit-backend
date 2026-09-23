package com.fundit.order.domain.funding;

/** #129 — 판매자 발송목록 탭에 표시할 발송상태별 건수. */
public record SellerOrderShippingCounts(long waiting, long shipped) {
}
