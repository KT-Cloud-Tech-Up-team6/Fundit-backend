package com.fundit.order.domain.funding;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.domain.OrderErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — 상태 전이 규칙(PENDING → ... )이 있다.
 * FundingLineItem/FundingLineItemOption은 같은 애그리거트에 속한 자식이라 별도 Repository가 없다.
 */
@Getter
@Builder(toBuilder = true)
public class Funding {

    private final Long id;
    private final UUID publicId;
    private final UUID memberId;
    private final Long projectId;
    private final String projectTitle;
    private final Long liveSessionId;
    private FundingStatus status;
    private final ShippingAddress shippingAddress;
    private final long shippingFee;
    private final Instant paymentExpiresAt;
    private Instant decidedAt;
    private final List<FundingLineItem> lineItems;
    private final Instant createdAt;

    public static Funding create(UUID memberId, Long projectId, String projectTitle, ShippingAddress shippingAddress,
                                  long shippingFee, List<FundingLineItem> lineItems, Instant paymentExpiresAt) {
        return Funding.builder()
                .publicId(UUID.randomUUID())
                .memberId(memberId)
                .projectId(projectId)
                .projectTitle(projectTitle)
                .status(FundingStatus.PENDING)
                .shippingAddress(shippingAddress)
                .shippingFee(shippingFee)
                .lineItems(lineItems)
                .paymentExpiresAt(paymentExpiresAt)
                .build();
    }

    public boolean isOwnedBy(UUID accountId) {
        return memberId.equals(accountId);
    }

    public boolean isCancellableStatus() {
        return status == FundingStatus.PENDING || status == FundingStatus.FUNDING_IN_PROGRESS;
    }

    /** ORDER-014 — 참여 취소(단순변심). 마감 전(PENDING/FUNDING_IN_PROGRESS)에만 가능하다. */
    public void cancelByMember() {
        if (status == FundingStatus.PAYMENT_EXPIRED) {
            throw new BusinessException(CommonErrorCode.RESOURCE_EXPIRED, "이미 만료된 주문입니다.");
        }
        if (!isCancellableStatus()) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELLABLE);
        }
        this.status = FundingStatus.CANCELLED_BY_MEMBER;
        this.decidedAt = Instant.now();
    }

    /**
     * ORDER-013 — 미결제 만료 배치 전용. API 호출 경로에서는 쓰지 않는다(배치가 유일한 전이 주체).
     *
     * @return true면 이번 호출로 만료 처리됨, false면 이미 PENDING이 아니라 아무 것도 하지 않음(idempotent)
     */
    public boolean expireIfPending() {
        if (status != FundingStatus.PENDING) {
            return false;
        }
        this.status = FundingStatus.PAYMENT_EXPIRED;
        this.decidedAt = Instant.now();
        return true;
    }

    /** ORDER-006 — 목표금액 달성 판정(성공). */
    public void markGoalAchieved() {
        this.status = FundingStatus.GOAL_ACHIEVED;
        this.decidedAt = Instant.now();
    }

    /** ORDER-006 — 목표금액 달성 판정(실패, 환불 트리거). */
    public void markGoalFailed() {
        this.status = FundingStatus.GOAL_FAILED_REFUNDED;
        this.decidedAt = Instant.now();
    }

    /**
     * payment-service PaymentCompleted 이벤트 처리 — PENDING을 결제 완료 상태로 전환한다.
     * ORDER-0XX로 별도 번호가 매겨져 있지 않지만, 그렇지 않으면 결제에 성공한 주문이 영영
     * PENDING에 머무르게 되어 필요한 전이다[가정 — ORDER-015(쿠폰 사용확정) 이벤트 처리와
     * 같은 이벤트를 트리거로 공유]. 이미 다른 상태면 무시한다(idempotent).
     */
    public void markPaymentCompleted() {
        if (status == FundingStatus.PENDING) {
            this.status = FundingStatus.FUNDING_IN_PROGRESS;
        }
    }

    /** 성립 후(하자/지연) 전액 환불 완료 — payment-service RefundCompleted 이벤트 처리[가정, 위와 동일]. */
    public void markRefundedAfterSuccess() {
        this.status = FundingStatus.REFUNDED_AFTER_SUCCESS;
        this.decidedAt = Instant.now();
    }

    public long totalRewardAmount() {
        return lineItems.stream().mapToLong(FundingLineItem::amount).sum();
    }

    /**
     * ORDER-005 응답용 가능 액션. 배송 완료 여부는 shipping-service 소관이라 이 애그리거트만으로는
     * 구분할 수 없어, GOAL_ACHIEVED 상태는 전부 SHIPPING_DELAY_REFUND_REQUEST로 단순화했다
     * [가정 — 배송완료 후 DEFECT_REFUND_REQUEST 구분은 shipping-service 연동 후 보강 필요].
     */
    public List<String> availableActions() {
        return switch (status) {
            case PENDING, FUNDING_IN_PROGRESS -> List.of("CANCEL");
            case GOAL_ACHIEVED -> List.of("SHIPPING_DELAY_REFUND_REQUEST");
            default -> List.of();
        };
    }
}
