package com.fundit.fulfillment.domain.schedulechange;

/**
 * FULFILLMENT-005 일정 변경·지연사유 화이트리스트(V1__init_schema.sql의 CHECK 제약과 동일한 값
 * 집합). 요청 DTO 필드 타입으로 직접 사용해 화이트리스트 밖 값은 Jackson 역직렬화 단계에서
 * 400(INVALID_INPUT)으로 걸러지게 한다.
 */
public enum ScheduleChangeReasonType {
    START_DELAY,
    STOCK_SHORTAGE,
    INSPECTION_DELAY,
    SHIPPING_DELAY,
    OTHER
}
