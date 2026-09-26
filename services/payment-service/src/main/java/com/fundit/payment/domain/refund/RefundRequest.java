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

    /** {@link #completeImmediately}가 허용하는 유형 — 실제 호출부(FundingLifecycleEventSyncService,
     * PaymentReconciliationService, ShippingDelayRefundService) 기준. SIMPLE_CHANGE_OF_MIND는
     * 모금 중 참여 취소 이벤트 경로로만 들어온다(성립 후 단순변심 취소는 정책상 불가). */
    private static final Set<RefundTriggerType> IMMEDIATE_TRIGGER_TYPES = EnumSet.of(
            RefundTriggerType.SIMPLE_CHANGE_OF_MIND, RefundTriggerType.GOAL_FAILED_AUTO,
            RefundTriggerType.SHIPPING_DELAY, RefundTriggerType.SYSTEM_RECONCILIATION);

    /** {@link #awaitingAlternateAccount}가 허용하는 유형 — 대체계좌 대기 경로
     * (executeFullRefundOrAwaitAlternateAccount)로 실제 호출되는 두 유형만 둔다. */
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
     * PAYMENT-004/005/008/017 — 판매자/운영자 검토 없이 즉시 처리되는 유형(단순변심/미달자동/
     * 발송지연/시스템 재조정). 토스 취소가 이미 성공했다는 전제로 곧바로 COMPLETED로 기록한다.
     */
    public static RefundRequest completeImmediately(RefundTriggerType triggerType, UUID fundingId, UUID paymentId,
                                                      boolean isFullRefund) {
        if (!IMMEDIATE_TRIGGER_TYPES.contains(triggerType)) {
            throw new IllegalArgumentException(triggerType + "는 즉시 처리 대상이 아닙니다.");
        }
        Instant now = Instant.now();
        return RefundRequest.builder()
                .fundingId(fundingId)
                .paymentId(paymentId)
                .triggerType(triggerType)
                .status(RefundRequestStatus.COMPLETED)
                .isFullRefund(isFullRefund)
                .requestedAt(now)
                .processedAt(now)
                .build();
    }

    /**
     * PAYMENT-005/008 예외 처리 — 원 결제수단으로 토스 취소가 불가능해(카드 만료/해지 등)
     * 참여자의 대체 계좌 입력을 기다려야 하는 상태. {@code COMPLETED}로 확정하지 않고
     * {@code REQUESTED}로 남겨 재처리 대상임을 표시한다.
     */
    public static RefundRequest awaitingAlternateAccount(RefundTriggerType triggerType, UUID fundingId,
                                                           UUID paymentId) {
        if (!ALTERNATE_ACCOUNT_TRIGGER_TYPES.contains(triggerType)) {
            throw new IllegalArgumentException(triggerType + "는 대체 계좌 대기 대상이 아닙니다.");
        }
        return RefundRequest.builder()
                .fundingId(fundingId)
                .paymentId(paymentId)
                .triggerType(triggerType)
                .status(RefundRequestStatus.REQUESTED)
                .requestedAt(Instant.now())
                .build();
    }

    /** PAYMENT-007 — 판매자 승인. 토스 취소 성공 후 호출한다(반품비 차감 시 부분취소 가능). */
    public void approve(boolean isFullRefund) {
        assertDecidable();
        this.status = RefundRequestStatus.COMPLETED;
        this.isFullRefund = isFullRefund;
        this.processedAt = Instant.now();
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
