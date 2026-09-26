package com.fundit.order.application.payment;

import java.util.List;
import java.util.UUID;

/**
 * ORDER-015 — payment-service가 발행하는 결제완료/환불 이벤트를 구독해 이 서비스 소유
 * 테이블(coupon_issuances, 그리고 fundings.status)을 자체적으로 갱신한다(DB-per-service 원칙).
 *
 * <p>payload의 {@code fundingId}는 내부 PK(Long)가 아니라 {@code Funding.publicId}(UUID)다 —
 * payment-service는 cross-service ID 통일(#69) 이후 UUID만 실어 보낸다. 이 인터페이스가
 * {@code Long fundingId}로 선언돼 있어 실제 페이로드와 맞지 않았고(역직렬화 실패) 성립 후 환불의
 * 주문 상태 전이가 동작하지 않았다 — 필드명(= Kafka payload 키)은 그대로 두고 타입만 맞춘다.
 */
public interface PaymentEventListener {

    /**
     * 결제 성공 — 이 이벤트를 트리거로 (1) 쿠폰을 사용완료 처리하고 (2) PENDING 상태의 Funding을
     * 결제 완료 상태로 전환한다. (2)는 ORDER-0XX로 번호가 매겨져 있지 않지만, 그렇지 않으면
     * 결제에 성공한 주문이 영영 PENDING에 머무르게 되어 필요한 처리다[가정].
     */
    void onPaymentCompleted(PaymentCompletedEvent event);

    /** 환불 완료 — 환불 유형에 따라 쿠폰 복원 여부와 Funding 상태를 함께 반영한다. */
    void onRefundCompleted(RefundCompletedEvent event);

    /** {@code couponIssuanceIds}는 이 주문에 적용된 쿠폰 전체(최대 2개, 플랫폼+메이커)다. */
    record PaymentCompletedEvent(UUID fundingId, List<Long> couponIssuanceIds) {
    }

    /**
     * GOAL_FAILURE_AUTO_REFUND: 목표 미달 자동환불(쿠폰 복원) / CANCELLED_BY_MEMBER: 마감 전
     * 단순변심 취소(쿠폰 미복원 — ORDER-014 API 흐름에서 이미 별도 처리되므로 여기서는 무시) /
     * POST_SUCCESS_DEFECT, POST_SUCCESS_DELAY: 성립 후 하자·지연 환불(전액환불 건만 복원) /
     * POST_SUCCESS_RETURN: 발송 후 구매자 귀책 반품(환불 정책 V.1.0 — 반품비가 차감된 부분
     * 환불이라 쿠폰은 복원하지 않지만 주문은 반품 완료로 전이한다).
     */
    enum RefundReason {
        GOAL_FAILURE_AUTO_REFUND, CANCELLED_BY_MEMBER, POST_SUCCESS_DEFECT, POST_SUCCESS_DELAY, POST_SUCCESS_RETURN
    }

    record RefundCompletedEvent(UUID fundingId, List<Long> couponIssuanceIds, RefundReason refundReason,
                                 boolean fullRefund) {
    }
}
