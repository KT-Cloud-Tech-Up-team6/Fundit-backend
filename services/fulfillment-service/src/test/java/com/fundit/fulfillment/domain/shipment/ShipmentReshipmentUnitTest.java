package com.fundit.fulfillment.domain.shipment;

import com.fundit.common.error.BusinessException;
import com.fundit.fulfillment.domain.FulfillmentErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 교환 재발송(payment-service 내부 API) — 배송을 새 사이클로 되돌리는 전이. */
class ShipmentReshipmentUnitTest {

    private static final UUID FUNDING_ID = new UUID(0L, 1024L);
    private static final UUID PROJECT_ID = new UUID(0L, 123L);

    @Test
    void 배송완료_건을_재발송하면_운송장이_비워지고_PREPARING으로_돌아간다() {
        // given
        Shipment shipment = delivered();

        // when
        boolean started = shipment.startReshipment(77L, Instant.now());

        // then
        assertThat(started).isTrue();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PREPARING);
        assertThat(shipment.getCarrier()).isNull();
        assertThat(shipment.getTrackingNumber()).isNull();
        assertThat(shipment.getShippedAt()).isNull();
        assertThat(shipment.getDeliveredAt()).isNull();
        assertThat(shipment.getReshipmentCount()).isEqualTo(1);
        assertThat(shipment.getLastReshipmentRefundRequestId()).isEqualTo(77L);
    }

    @Test
    void 재발송_후_새_운송장을_등록할_수_있다() {
        // given
        Shipment shipment = delivered();
        shipment.startReshipment(77L, Instant.now());

        // when
        shipment.registerShipment("한진택배", "999888777");

        // then
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(shipment.getTrackingNumber()).isEqualTo("999888777");
    }

    @Test
    void 같은_교환_신청으로_다시_요청하면_상태를_되돌리지_않는다() {
        // given — 재발송 후 판매자가 이미 새 운송장을 등록한 상태
        Shipment shipment = delivered();
        shipment.startReshipment(77L, Instant.now());
        shipment.registerShipment("한진택배", "999888777");

        // when — 내부 호출 재시도
        boolean started = shipment.startReshipment(77L, Instant.now());

        // then
        assertThat(started).isFalse();
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(shipment.getTrackingNumber()).isEqualTo("999888777");
        assertThat(shipment.getReshipmentCount()).isEqualTo(1);
    }

    @Test
    void 수령확인된_건도_재발송할_수_있다() {
        // given
        Shipment shipment = delivered();
        shipment.confirmReceipt(Instant.now(), false);

        // when
        boolean started = shipment.startReshipment(78L, Instant.now());

        // then
        assertThat(started).isTrue();
        assertThat(shipment.getReceiptConfirmedAt()).isNull();
    }

    @Test
    void 배송_전_건은_재발송할_수_없다() {
        // given — 아직 배송 중(SHIPPED)
        Shipment shipment = Shipment.create(FUNDING_ID, PROJECT_ID);
        shipment.registerShipment("CJ대한통운", "123456789012");

        // when & then
        assertThatThrownBy(() -> shipment.startReshipment(77L, Instant.now()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.RESHIPMENT_NOT_ALLOWED));
    }

    private static Shipment delivered() {
        Shipment shipment = Shipment.create(FUNDING_ID, PROJECT_ID);
        shipment.registerShipment("CJ대한통운", "123456789012");
        shipment.markDelivered(Instant.now());
        return shipment;
    }
}
