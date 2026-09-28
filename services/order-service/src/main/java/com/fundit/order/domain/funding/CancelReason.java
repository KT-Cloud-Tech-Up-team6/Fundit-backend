package com.fundit.order.domain.funding;

/**
 * 참여 취소 사유(ORDER-014, IA v1.3 화면 {@code FL_B_MY_FUND_CL}). 취소 가능 여부를 바꾸지 않는
 * 기록용 값이다 — 어떤 사유든 마감 전이면 취소되고, 어느 쪽도 위약금·처리 차이가 없다.
 */
public enum CancelReason {
    SIMPLE_CHANGE_OF_MIND,
    PAYMENT_INFO_ERROR,
    OPTION_SELECTION_ERROR,
    /** 기타 — 상세 입력(100자 이내)이 필수다. */
    ETC;

    public boolean requiresDetail() {
        return this == ETC;
    }
}
