package com.fundit.fulfillment.domain.shipment;

/** 펀딩(주문) 단위 발송·수령 상태. 선언 순서가 곧 진행 순서다(순방향 전이만 허용). */
public enum ShipmentStatus {
    PREPARING,
    SHIPPED,
    DELIVERED,
    RECEIPT_CONFIRMED
}
