package com.fundit.fulfillment.domain.tracker;

/**
 * 제작·배송 5단계. 선언 순서가 곧 진행 순서이며, {@link FulfillmentTracker}는 이 순서를
 * 기준으로 순방향 전이만 허용한다(V1__init_schema.sql의 CHECK 제약과 동일한 값 집합).
 */
public enum FulfillmentStage {
    PRODUCTION_START,
    MANUFACTURING,
    INSPECTION,
    SHIPPING_OUT,
    DELIVERY;

    public boolean isBefore(FulfillmentStage other) {
        return this.ordinal() < other.ordinal();
    }
}
