package com.fundit.payment.domain.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.domain.PaymentErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — trigger_type별로 상태 전이 규칙이 다르다.
 */
@Getter
@Builder(toBuilder = true)
public class RefundRequest {

    private final Long id;
    /** order-service {@code Funding.publicId}(외부 노출 orderId). */
    private final UUID fundingId;
    private final UUID paymentId;
    /** 판매자 환불 목록 조회용 — DEFECT 신청 시점에만 채워진다(그 외 유형은 판매자 검토 대상이 아니라 null). */
    private final UUID sellerId;
    private final RefundTriggerType triggerType;
    private RefundRequestStatus status;
    private Boolean isFullRefund;
    private final String reasonDetail;
    private final List<String> evidenceUrls;
    private String rejectedReason;
    private AlternateRefundAccount alternateRefundAccount;
    private final Instant requestedAt;
    private Instant processedAt;
    /**
     * 교환 재발송을 fulfillment-service에 실제로 요청한 시각. 교환이 PROCESSING인데 이 값이
     * null이면 요청이 아직(또는 실패해서) 안 된 것이고, 재시도 워커가 그 행을 집어 다시 보낸다.
     */
    private Instant reshipmentRequestedAt;

    /**
     * 토스 취소를 요청했지만 아직 확정되지 않은 금액·사유·시각(V11). 금액이 있고 상태가 PROCESSING이면
     * "취소 요청됨"이다 — 교환의 PROCESSING(재발송 대기)은 금액이 없어 구분된다. 확정되거나 되돌려지면
     * 요청 시각을 비워 배치 대상에서 빠진다.
     */
    private Long cancelAmount;
    private String cancelReason;
    private Instant cancelRequestedAt;

    /** {@link #requestCancel}이 허용하는 유형 — 판매자/운영자 검토 없이 즉시 취소하는 경로(004/005/008/017). */
    private static final Set<RefundTriggerType> IMMEDIATE_TRIGGER_TYPES = EnumSet.of(
            RefundTriggerType.SIMPLE_CHANGE_OF_MIND, RefundTriggerType.GOAL_FAILED_AUTO,
            RefundTriggerType.SHIPPING_DELAY, RefundTriggerType.SYSTEM_RECONCILIATION);

    /** 원 결제수단 취소가 거절되면 대체 계좌 입력 대기로 넘기는 유형(PAYMENT-005/008 예외 처리). */
    private static final Set<RefundTriggerType> ALTERNATE_ACCOUNT_TRIGGER_TYPES = EnumSet.of(
            RefundTriggerType.GOAL_FAILED_AUTO, RefundTriggerType.SHIPPING_DELAY);

    /**
     * PAYMENT-006 / 환불 정책 V.1.0 — 발송 후 신청(하자환불·교환·구매자 귀책 반품)을 판매자
     * 검토 대기(REQUESTED)로 접수한다. 증빙은 **판매자 귀책(DEFECT)일 때만 필수**다 — 단순변심
     * 반품·교환은 구매자 귀책이라 입증 자료를 요구하지 않는다(환불 정책 V.1.0 공통 정책 표).
     *
     * <p>교환(EXCHANGE)은 승인/완료(재발송)가 아직 없어 접수·조회까지만 의미가 있다.
     */
    public static RefundRequest requestAfterShipment(RefundTriggerType triggerType, UUID fundingId, UUID paymentId,
                                                      UUID sellerId, String reasonDetail, List<String> evidenceUrls) {
        if (!triggerType.isPostShipmentRequest()) {
            throw new IllegalArgumentException(triggerType + "는 발송 후 신청 대상이 아닙니다.");
        }
        if (triggerType == RefundTriggerType.DEFECT && (evidenceUrls == null || evidenceUrls.isEmpty())) {
            throw new BusinessException(PaymentErrorCode.EVIDENCE_REQUIRED);
        }
        return RefundRequest.builder()
                .fundingId(fundingId)
                .paymentId(paymentId)
                .sellerId(sellerId)
                .triggerType(triggerType)
                .status(RefundRequestStatus.REQUESTED)
                .reasonDetail(reasonDetail)
                .evidenceUrls(evidenceUrls)
                .build();
    }

    /**
     * PAYMENT-004/005/008/017 — 판매자/운영자 검토 없이 즉시 취소하는 유형. 토스를 부르기 전에 "취소 요청됨"
     * (PROCESSING)으로 먼저 커밋한다 — 취소는 성공했는데 확정 기록이 실패해도 이 행이 남아 대사 배치가 맞춘다.
     *
     * @param cancelReason 취소 사유(참여 취소 사유 태그 포함). 취소 내역의 {@code reasonType}·{@code reasonDetail}이
     *                     여기서 나오고, 토스 취소 사유로도 그대로 쓴다
     */
    public static RefundRequest requestCancel(RefundTriggerType triggerType, UUID fundingId, UUID paymentId,
                                              long cancelAmount, String cancelReason) {
        if (!IMMEDIATE_TRIGGER_TYPES.contains(triggerType)) {
            throw new IllegalArgumentException(triggerType + "는 즉시 처리 대상이 아닙니다.");
        }
        Instant now = Instant.now();
        return RefundRequest.builder()
                .fundingId(fundingId)
                .paymentId(paymentId)
                .triggerType(triggerType)
                .status(RefundRequestStatus.PROCESSING)
                .reasonDetail(cancelReason)
                .cancelAmount(cancelAmount)
                .cancelReason(cancelReason)
                .cancelRequestedAt(now)
                .requestedAt(now)
                .build();
    }

    /** PAYMENT-007 — 판매자 승인. 토스를 부르기 전에 "취소 요청됨"으로 전이한다(반품비 차감 시 부분취소 금액). */
    public void startCancel(long cancelAmount, String cancelReason) {
        assertDecidable();
        this.status = RefundRequestStatus.PROCESSING;
        this.cancelAmount = cancelAmount;
        this.cancelReason = cancelReason;
        this.cancelRequestedAt = Instant.now();
    }

    public boolean isCancelInFlight() {
        return status == RefundRequestStatus.PROCESSING && cancelAmount != null;
    }

    /** 토스 취소가 확인된 뒤 확정한다. 금액·사유는 기록으로 남기고 요청 시각만 비운다. */
    public void completeCancel(boolean isFullRefund) {
        assertCancelInFlight();
        this.status = RefundRequestStatus.COMPLETED;
        this.isFullRefund = isFullRefund;
        this.processedAt = Instant.now();
        this.cancelRequestedAt = null;
    }

    /** 토스가 취소를 거절했을 때 대체 계좌 입력 대기({@link #awaitAlternateAccount})로 넘길 수 있는 유형인지. */
    public boolean canAwaitAlternateAccount() {
        return ALTERNATE_ACCOUNT_TRIGGER_TYPES.contains(triggerType);
    }

    /**
     * PAYMENT-005/008 예외 처리 — 원 결제수단으로 토스 취소가 거절돼(카드 만료/해지 등) 참여자의 대체 계좌
     * 입력을 기다린다. {@code COMPLETED}로 확정하지 않고 {@code REQUESTED}로 남겨 재처리 대상임을 표시한다.
     */
    public void awaitAlternateAccount() {
        assertCancelInFlight();
        if (!canAwaitAlternateAccount()) {
            throw new IllegalStateException(triggerType + "는 대체 계좌 대기 대상이 아닙니다.");
        }
        clearCancelRequest();
    }

    /** PAYMENT-007 — 승인 후 토스 취소가 거절됐다. 판매자가 다시 결정할 수 있게 검토 대기로 되돌린다. */
    public void revertCancel() {
        assertCancelInFlight();
        if (!triggerType.isSellerDecisionTarget()) {
            throw new IllegalStateException(triggerType + "는 판매자 검토 대상이 아닙니다.");
        }
        clearCancelRequest();
    }

    private void clearCancelRequest() {
        this.status = RefundRequestStatus.REQUESTED;
        this.cancelAmount = null;
        this.cancelReason = null;
        this.cancelRequestedAt = null;
    }

    private void assertCancelInFlight() {
        if (!isCancelInFlight()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "취소 요청 중인 환불이 아닙니다.");
        }
    }

    /**
     * 교환 승인(구매자 귀책) — 교환 배송비를 구매자가 별도 결제해야 하므로 결제 대기 상태로 둔다.
     * 결제가 완료되면 {@link #startExchangeReshipment()}로 재발송 단계로 넘어간다.
     */
    public void approveExchangeAwaitingFee() {
        assertExchange();
        assertDecidable();
        this.status = RefundRequestStatus.APPROVED;
    }

    /**
     * 교환 재발송 요청 완료 — 판매자 귀책(교환비 0원)은 승인 즉시, 구매자 귀책은 교환비 결제
     * 완료 직후 호출된다. 결제취소가 없어 {@code isFullRefund}는 채우지 않는다.
     */
    public void startExchangeReshipment() {
        assertExchange();
        if (status != RefundRequestStatus.REQUESTED && status != RefundRequestStatus.UNDER_REVIEW
                && status != RefundRequestStatus.APPROVED) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "재발송을 시작할 수 있는 상태가 아닙니다.");
        }
        this.status = RefundRequestStatus.PROCESSING;
    }

    /**
     * 교환 완료 — 재발송분의 배송이 끝난 시점(fulfillment 배송완료 이벤트)에 종료 처리한다.
     * 이벤트는 중복 수신될 수 있어 이미 완료된 건은 그대로 둔다(멱등).
     */
    public void completeExchange() {
        assertExchange();
        if (status == RefundRequestStatus.COMPLETED) {
            return;
        }
        if (status != RefundRequestStatus.PROCESSING) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "재발송 진행 중인 교환 신청이 아닙니다.");
        }
        this.status = RefundRequestStatus.COMPLETED;
        this.processedAt = Instant.now();
    }

    /** fulfillment 재발송 요청이 실제로 성공했음을 기록한다 — 재시도 워커의 대상에서 빠진다. */
    public void markReshipmentRequested() {
        assertExchange();
        this.reshipmentRequestedAt = Instant.now();
    }

    private void assertExchange() {
        if (triggerType != RefundTriggerType.EXCHANGE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "교환 신청이 아닙니다.");
        }
    }

    /** PAYMENT-007 — 판매자 반려. 사유 필수. */
    public void reject(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(PaymentErrorCode.REASON_REQUIRED);
        }
        assertDecidable();
        this.status = RefundRequestStatus.REJECTED;
        this.rejectedReason = reason;
        this.processedAt = Instant.now();
    }

    private void assertDecidable() {
        if (status != RefundRequestStatus.REQUESTED && status != RefundRequestStatus.UNDER_REVIEW) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "이미 처리된 환불 신청입니다.");
        }
    }

    /** PAYMENT-005/008 — 원 결제수단 환불 불가 시 대체 계좌 입력. */
    public void useAlternateAccount(AlternateRefundAccount account) {
        this.alternateRefundAccount = account;
    }
}
