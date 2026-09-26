package com.fundit.payment.domain.refund;

/**
 * 교환 사유. 환불 정책 V.1.0 「취소·반품·교환 공통 정책」이 사유별로 교환 배송비 부담 주체를
 * 나누므로, 사유 자체가 금액(구매자 추가 결제액)을 결정한다 — 사전 계산(RefundEstimateService)과
 * 접수·조회 응답이 같은 규칙을 써야 해서 판정을 이 enum에 둔다.
 */
public enum ExchangeReason {

    /** 구매자 귀책 — 교환 배송비를 구매자가 부담한다. */
    CHANGE_OF_MIND(Fault.BUYER),
    WRONG_OPTION(Fault.BUYER),
    /** 판매자 귀책 — 구매자 부담 없음. 다만 귀책은 판매자 검토로 확정된다. */
    DEFECTIVE(Fault.SELLER),
    DAMAGED(Fault.SELLER),
    WRONG_DELIVERY(Fault.SELLER),
    MISSING_COMPONENTS(Fault.SELLER),
    DIFFERENT_FROM_DESCRIPTION(Fault.SELLER),
    /** 기타·귀책 불분명 — 접수 후 검토로 정한다. 신청 시점에 금액을 확정해 표시하지 않는다. */
    OTHER(Fault.UNDETERMINED);

    private final Fault fault;

    ExchangeReason(Fault fault) {
        this.fault = fault;
    }

    /** 사유를 지정하지 않았거나 알 수 없는 값이면 기타로 본다(금액 0원, 확정 아님). */
    public static ExchangeReason orOther(String name) {
        if (name == null) {
            return OTHER;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }

    /** 구매자가 별도 결제해야 하는 금액. 판매자 귀책·기타는 0원이다. */
    public long additionalPaymentAmount() {
        return fault == Fault.BUYER ? ReturnPolicy.EXCHANGE_SHIPPING_FEE : 0L;
    }

    /**
     * 신청 시점에 금액을 확정액으로 표시해도 되는지. 구매자 귀책만 true다 — 판매자 귀책·기타는
     * 판매자 검토에서 귀책이 뒤집히면 부담액이 달라지므로 "확인 시"로 표시해야 한다.
     */
    public boolean isAmountConfirmed() {
        return fault == Fault.BUYER;
    }

    private enum Fault {
        BUYER, SELLER, UNDETERMINED
    }
}
