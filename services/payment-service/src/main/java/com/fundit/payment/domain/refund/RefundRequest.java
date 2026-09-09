package com.fundit.payment.domain.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.domain.PaymentErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — trigger_type별로 상태 전이 규칙이 다르다.
 */
@Getter
@Builder(toBuilder = true)
public class RefundRequest {

    private final Long id;
    private final Long fundingId;
    private final UUID paymentId;
    private final RefundTriggerType triggerType;
    private RefundRequestStatus status;
    private Boolean isFullRefund;
    private final String reasonDetail;
    private final List<String> evidenceUrls;
    private String rejectedReason;
    private AlternateRefundAccount alternateRefundAccount;
    private final Instant requestedAt;
    private Instant processedAt;

    /** PAYMENT-006 — 하자환불 신청. 증빙 누락 시 신청 자체를 차단한다. */
    public static RefundRequest requestDefect(Long fundingId, UUID paymentId, String reasonDetail,
                                               List<String> evidenceUrls) {
        if (evidenceUrls == null || evidenceUrls.isEmpty()) {
            throw new BusinessException(PaymentErrorCode.EVIDENCE_REQUIRED);
        }
        return RefundRequest.builder()
                .fundingId(fundingId)
                .paymentId(paymentId)
                .triggerType(RefundTriggerType.DEFECT)
                .status(RefundRequestStatus.REQUESTED)
                .reasonDetail(reasonDetail)
                .evidenceUrls(evidenceUrls)
                .build();
    }

    /**
     * PAYMENT-004/005/008/017 — 판매자/운영자 검토 없이 즉시 처리되는 유형(단순변심/미달자동/
     * 발송지연/시스템 재조정). 토스 취소가 이미 성공했다는 전제로 곧바로 COMPLETED로 기록한다.
     */
    public static RefundRequest completeImmediately(RefundTriggerType triggerType, Long fundingId, UUID paymentId,
                                                      boolean isFullRefund) {
        if (triggerType == RefundTriggerType.DEFECT) {
            throw new IllegalArgumentException("DEFECT는 즉시 처리 대상이 아닙니다(판매자 검토 필요).");
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
    public static RefundRequest awaitingAlternateAccount(RefundTriggerType triggerType, Long fundingId,
                                                           UUID paymentId) {
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
