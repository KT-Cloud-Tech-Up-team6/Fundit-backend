package com.fundit.fulfillment.domain.shipment;

import com.fundit.common.error.BusinessException;
import com.fundit.fulfillment.domain.FulfillmentErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — {@code PREPARING→SHIPPED→DELIVERED→
 * RECEIPT_CONFIRMED} 상태 전이 불변식이 있다. 판매자가 발송 등록이나 임시저장(FULFILLMENT-006)을
 * 하기 전까지는 이 애그리거트 자체가 존재하지 않는다(지연 생성).
 *
 * <p>행이 있다고 발송된 것은 아니다 — 임시저장으로 {@code PREPARING} 행이 먼저 생길 수 있어,
 * 발송 여부는 반드시 {@code status}로 판정한다(행 존재로 판정하지 말 것).
 */
@Getter
@Builder(toBuilder = true)
public class Shipment {

    private final Long id;
    private final UUID fundingId;
    private final UUID projectId;
    private ShipmentStatus status;
    private String carrier;
    private String trackingNumber;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant receiptConfirmedAt;
    private boolean receiptAutoConfirmed;
    /** 교환으로 다시 보낸 횟수. 0이면 최초 발송 사이클이다. */
    private int reshipmentCount;
    private Instant lastReshipmentRequestedAt;
    /** 마지막 재발송을 유발한 payment-service 교환 신청(refund_requests.id) — 멱등 판정에 쓴다. */
    private Long lastReshipmentRefundRequestId;
    private final Instant createdAt;
    private Instant updatedAt;

    /** FULFILLMENT-006 — 발송정보 등록 시 shipments 행 자체가 없으면 PREPARING으로 새로 만든다. */
    public static Shipment create(UUID fundingId, UUID projectId) {
        return Shipment.builder()
                .fundingId(fundingId)
                .projectId(projectId)
                .status(ShipmentStatus.PREPARING)
                .receiptAutoConfirmed(false)
                .build();
    }

    /** FULFILLMENT-006 — 택배사·운송장 등록과 동시에 발송 완료 상태로 전환한다(목업, 실연동 없음). */
    public void registerShipment(String carrier, String trackingNumber) {
        if (status.ordinal() >= ShipmentStatus.SHIPPED.ordinal()) {
            throw new BusinessException(FulfillmentErrorCode.ALREADY_SHIPPED);
        }
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.status = ShipmentStatus.SHIPPED;
        this.shippedAt = Instant.now();
    }

    /**
     * FULFILLMENT-006 — 발송 전 택배사·운송장 임시저장. 상태 전이도, 이벤트 발행도 없다
     * (실제 발송 처리는 {@link #registerShipment}).
     */
    public void saveShippingInfo(String carrier, String trackingNumber) {
        if (status.ordinal() >= ShipmentStatus.SHIPPED.ordinal()) {
            throw new BusinessException(FulfillmentErrorCode.ALREADY_SHIPPED);
        }
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
    }

    /**
     * 교환 재발송 착수 — payment-service가 교환 승인(판매자 귀책) 또는 교환비 결제 완료 후
     * 내부 API로 요청한다. 배송을 새 사이클로 되돌리므로 운송장·배송완료·수령확인 값을 비우고
     * {@code PREPARING}으로 내린다. 판매자는 기존 발송정보 등록(FULFILLMENT-006)으로 새 운송장을
     * 올리면 되고, 그 시점에 {@code shipment.shipped.v1}이 다시 발행된다.
     *
     * <p>같은 교환 신청으로 두 번 요청되면(내부 호출 재시도) 아무것도 바꾸지 않는다 — 판매자가
     * 이미 새 운송장을 등록한 뒤 늦게 도착한 재시도가 그것을 지우면 안 된다.
     *
     * <p>배송이 끝난 건만 대상이다. 수령 후 7일 이내 신청이라는 정책상 교환은 배송완료
     * (또는 수령확인) 이후에만 접수되므로, 그 외 상태로 오는 요청은 잘못된 호출이다.
     */
    public boolean startReshipment(Long refundRequestId, Instant at) {
        if (refundRequestId != null && refundRequestId.equals(lastReshipmentRefundRequestId)) {
            return false;
        }
        if (status != ShipmentStatus.DELIVERED && status != ShipmentStatus.RECEIPT_CONFIRMED) {
            throw new BusinessException(FulfillmentErrorCode.RESHIPMENT_NOT_ALLOWED);
        }
        this.status = ShipmentStatus.PREPARING;
        this.carrier = null;
        this.trackingNumber = null;
        this.shippedAt = null;
        this.deliveredAt = null;
        this.receiptConfirmedAt = null;
        this.receiptAutoConfirmed = false;
        this.reshipmentCount = reshipmentCount + 1;
        this.lastReshipmentRequestedAt = at;
        this.lastReshipmentRefundRequestId = refundRequestId;
        return true;
    }

    /** FULFILLMENT-007 — 배송완료 목업 배치 전용. */
    public void markDelivered(Instant at) {
        this.status = ShipmentStatus.DELIVERED;
        this.deliveredAt = at;
    }

    /**
     * FULFILLMENT-009/010 — 수령 확인(구매자 직접 또는 자동확정 배치). 이미 확정된 건은
     * idempotent하게 무시하고, 배송완료 전이면 예외를 던진다.
     */
    public void confirmReceipt(Instant at, boolean auto) {
        if (status == ShipmentStatus.RECEIPT_CONFIRMED) {
            return;
        }
        if (status != ShipmentStatus.DELIVERED) {
            throw new BusinessException(FulfillmentErrorCode.NOT_YET_DELIVERED);
        }
        this.status = ShipmentStatus.RECEIPT_CONFIRMED;
        this.receiptConfirmedAt = at;
        this.receiptAutoConfirmed = auto;
    }

    public boolean canConfirmReceipt() {
        return status == ShipmentStatus.DELIVERED;
    }
}
