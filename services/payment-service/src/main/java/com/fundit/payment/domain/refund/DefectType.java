package com.fundit.payment.domain.refund;

/**
 * 하자환불 사유 유형(PAYMENT-006). 모두 판매자 귀책이라 승인 시 전액 취소이고, {@link #OTHER}만
 * 귀책이 불분명해 신청 시점에 확정액을 표시하지 않는다(환불 정책 V.1.0). 사전 계산과 요청 DTO가
 * 같은 값을 쓰므로 도메인에 둔다.
 */
public enum DefectType {
    DEFECTIVE, DAMAGED, WRONG_DELIVERY, DIFFERENT_FROM_DESCRIPTION, MISSING_COMPONENTS, OTHER
}
