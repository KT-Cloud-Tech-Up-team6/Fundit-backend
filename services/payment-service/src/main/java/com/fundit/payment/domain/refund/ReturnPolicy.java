package com.fundit.payment.domain.refund;

import java.time.Duration;
import java.time.Instant;

/**
 * 환불 정책 V.1.0 「취소·반품·교환 공통 정책」의 발송 후 반품·교환 규칙. 신청 접수
 * (PostShipmentRefundRequestService), 승인 금액 산정(DefectRefundDecisionService), 사전 계산
 * (RefundEstimateService)이 같은 값을 써야 해서 한곳에 모았다.
 *
 * <p>반품비는 전 프로젝트 공통 5,000원이다(정책 확정, 2026-09-23). 프로젝트/리워드별 반품비가
 * 생기면 project-service 정책 조회로 바꾼다 — 그때까지 설정값으로 빼지 않는다.
 */
public final class ReturnPolicy {

    /** 구매자 귀책(단순변심·옵션 선택 오류) 반품 시 구매자가 부담하는 반품 배송비. */
    public static final long RETURN_SHIPPING_FEE = 5_000L;

    /**
     * 구매자 귀책 교환 시 구매자가 별도 결제하는 교환 배송비. 지금은 반품비와 금액이 같지만
     * 정책상 다른 항목이라 상수를 따로 둔다(한쪽만 바뀔 수 있다).
     */
    public static final long EXCHANGE_SHIPPING_FEE = 5_000L;

    /** "수령 후 7일 이내 신청" — 기준일은 배송 완료(deliveredAt)다. */
    public static final Duration REQUEST_WINDOW = Duration.ofDays(7);

    private ReturnPolicy() {
    }

    /**
     * 수령 후 7일이 지나지 않았는지. 기준일을 receiptConfirmedAt(수령 확인)이 아니라
     * deliveredAt으로 두는 이유는, 수령 확인이 배송 완료 7일 뒤 자동 확정되어 그것을 기준으로
     * 하면 실제 신청 가능 기간이 14일로 늘어나기 때문이다.
     */
    public static boolean isWithinRequestWindow(Instant deliveredAt, Instant now) {
        return !now.isAfter(deliveredAt.plus(REQUEST_WINDOW));
    }

    /** 반품 배송비를 뺀 실제 환불액. */
    public static long refundAmountAfterReturnFee(long paymentAmount) {
        return paymentAmount - RETURN_SHIPPING_FEE;
    }

    /** 결제액이 반품비 이하면 차감 후 환불액이 남지 않아 신청 자체를 받을 수 없다. */
    public static boolean coversReturnFee(long paymentAmount) {
        return paymentAmount > RETURN_SHIPPING_FEE;
    }
}
