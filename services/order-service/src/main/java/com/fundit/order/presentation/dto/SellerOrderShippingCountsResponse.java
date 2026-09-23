package com.fundit.order.presentation.dto;

/** #129 — 판매자 발송목록 탭(발송대기/발송완료) 건수. */
public record SellerOrderShippingCountsResponse(long waiting, long shipped) {
}
