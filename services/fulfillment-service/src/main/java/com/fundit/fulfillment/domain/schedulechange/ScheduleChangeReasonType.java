package com.fundit.fulfillment.domain.schedulechange;

/**
 * FULFILLMENT-005 일정 변경·지연사유 화이트리스트(V1__init_schema.sql의 CHECK 제약과 동일한 값
 * 집합). 요청 DTO 필드 타입으로 직접 사용해 화이트리스트 밖 값은 Jackson 역직렬화 단계에서
 * 400(INVALID_INPUT)으로 걸러지게 한다.
 */
public enum ScheduleChangeReasonType {
    START_DELAY("착수 지연"),
    STOCK_SHORTAGE("재고 부족"),
    INSPECTION_DELAY("검수 지연"),
    SHIPPING_DELAY("배송 지연"),
    OTHER("기타");

    /** 타임라인에 보이는 한글 사유. 기록이 없던 단계의 상세내용을 채울 때 enum 원문 대신 쓴다. */
    private final String label;

    ScheduleChangeReasonType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
