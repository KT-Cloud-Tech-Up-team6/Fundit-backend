package com.fundit.order.domain.funding;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.domain.OrderErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
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

    /**
     * 반품·교환 신청 가능 기간(환불 정책 V.1.0 "수령 후 7일 이내 신청"). 접수 가능 여부의 최종
     * 판단은 payment-service가 하고, 여기서는 화면 버튼 노출만 맞춘다 — 같은 값이 두 서비스에
     * 있는 것은 의도적이다(서로 다른 목적이라 계약 모듈로 끌어올리지 않는다).
     */
    private static final Duration RETURN_REQUEST_WINDOW = Duration.ofDays(7);

    private final Long id;
    private final UUID publicId;
    private final UUID memberId;
    /**
     * project-service의 publicId(UUID) — cross-service ID 통일(#69) 이전에는 project-service
     * 내부 Long PK를 그대로 들고 있었다. 레거시 Long 값은 DB의 {@code project_id} 컬럼에만
     * 과거 데이터 조회·백필용으로 남아 있고, 도메인 레벨에서는 더 이상 추적하지 않는다.
     */
    private final UUID projectId;
    private final String projectTitle;
    private final Long liveSessionId;
    private FundingStatus status;
    private final ShippingAddress shippingAddress;
    private final long shippingFee;
    private final Instant paymentExpiresAt;
    private Instant decidedAt;
    /**
     * #129 — fulfillment-service {@code shipment.shipped.v1} 구독으로만 채워진다(조건부 UPDATE,
     * {@link FundingRepository#markShipped}). 이 값을 설정하는 도메인 메서드는 없다 — 단순히
     * hydrate→save 왕복 시 기존 값을 날리지 않고 그대로 실어 나르는 통과용 필드다.
     */
    private final Instant shippedAt;
    private final List<FundingLineItem> lineItems;
    private final Instant createdAt;
    /** ORDER-003 멱등 키(Idempotency-Key 헤더, 선택값) — 회원 범위 유니크. */
    private final String idempotencyKey;
    /** 같은 키로 다른 요청 본문이 오는 것을 구분하기 위한 요청 해시. idempotencyKey가 없으면 null. */
    private final String idempotencyRequestHash;

    /**
     * {@code liveSessionId}는 방송 중 생성된 주문에만 붙는 꼬리표다(비-라이브 주문은 null).
     * 게이트가 아니라 집계용 표식이라, 호출부가 live 조회에 실패하면 null을 넘기고 주문은 그대로 진행한다.
     */
    public static Funding create(UUID memberId, UUID projectId, String projectTitle, ShippingAddress shippingAddress,
                                  long shippingFee, List<FundingLineItem> lineItems, Instant paymentExpiresAt,
                                  String idempotencyKey, String idempotencyRequestHash, Long liveSessionId) {
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
                .idempotencyKey(idempotencyKey)
                .idempotencyRequestHash(idempotencyRequestHash)
                .liveSessionId(liveSessionId)
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

    /**
     * 성립 후 환불 완료 — payment-service RefundCompleted 이벤트 처리[가정, 위와 동일]. 하자·지연은
     * 전액 환불 건만, 반품(POST_SUCCESS_RETURN)은 반품비가 차감된 부분 환불이라도 여기로 온다
     * (반품이 완료된 주문은 "반품됨"으로 보여야 한다 — 환불 정책 V.1.0).
     */
    public void markRefundedAfterSuccess() {
        this.status = FundingStatus.REFUNDED_AFTER_SUCCESS;
        this.decidedAt = Instant.now();
    }

    public long totalRewardAmount() {
        return lineItems.stream().mapToLong(FundingLineItem::amount).sum();
    }

    /**
     * ORDER-005 응답용 가능 액션. 배송 상태는 fulfillment-service 소관이라 이 애그리게이트만으로는
     * 판단할 수 없어, 호출부({@link com.fundit.order.application.order.OrderQueryService})가
     * fulfillment-service 조회 결과를 넘겨준다 — GOAL_ACHIEVED가 아니면 조회 자체를 생략하고
     * 기본값(false, false)을 넘겨도 결과가 같다.
     */
    public List<String> availableActions(boolean isAlreadyShipped, Instant deliveredAt) {
        return switch (status) {
            case PENDING, FUNDING_IN_PROGRESS -> List.of("CANCEL");
            case GOAL_ACHIEVED -> {
                if (deliveredAt != null) {
                    // 환불 정책 V.1.0 — 반품·교환은 수령(배송 완료) 후 7일 이내만 신청할 수 있다.
                    // 기간이 지나면 버튼 자체를 내려주지 않는다(payment-service가 접수도 거절한다).
                    yield Instant.now().isAfter(deliveredAt.plus(RETURN_REQUEST_WINDOW))
                            ? List.of()
                            : List.of("RETURN_REQUEST", "EXCHANGE_REQUEST", "DEFECT_REFUND_REQUEST");
                }
                if (!isAlreadyShipped) {
                    yield List.of("SHIPPING_DELAY_REFUND_REQUEST");
                }
                yield List.of();
            }
            default -> List.of();
        };
    }
}
