package com.fundit.fulfillment.domain.shipment;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentUnitTest {

    @Test
    void 생성하면_PREPARING_상태로_시작한다() {
        // when
        Shipment shipment = Shipment.create(1024L, 123L);

        // then
        assertThat(shipment.getFundingId()).isEqualTo(1024L);
        assertThat(shipment.getProjectId()).isEqualTo(123L);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PREPARING);
        assertThat(shipment.canConfirmReceipt()).isFalse();
    }

    @Test
    void 발송정보를_등록하면_SHIPPED로_전환된다() {
        // given
        Shipment shipment = Shipment.create(1024L, 123L);

        // when
        shipment.registerShipment("CJ대한통운", "123456789012");

        // then
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(shipment.getCarrier()).isEqualTo("CJ대한통운");
        assertThat(shipment.getTrackingNumber()).isEqualTo("123456789012");
        assertThat(shipment.getShippedAt()).isNotNull();
    }

    @Test
    void 배송완료_처리하면_DELIVERED로_전환되고_수령확인이_가능해진다() {
        // given
        Shipment shipment = Shipment.create(1024L, 123L);
        shipment.registerShipment("CJ대한통운", "123456789012");

        // when
        shipment.markDelivered(Instant.now());

        // then
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(shipment.canConfirmReceipt()).isTrue();
    }

    @Test
    void 배송완료_후_수령확인하면_RECEIPT_CONFIRMED로_전환된다() {
        // given
        Shipment shipment = Shipment.create(1024L, 123L);
        shipment.registerShipment("CJ대한통운", "123456789012");
        shipment.markDelivered(Instant.now());

        // when
        shipment.confirmReceipt(Instant.now(), false);

        // then
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.RECEIPT_CONFIRMED);
        assertThat(shipment.isReceiptAutoConfirmed()).isFalse();
        assertThat(shipment.getReceiptConfirmedAt()).isNotNull();
    }

    @Test
    void 이미_수령확인된_건에_다시_요청하면_idempotent하게_유지된다() {
        // given
        Shipment shipment = Shipment.create(1024L, 123L);
        shipment.registerShipment("CJ대한통운", "123456789012");
        shipment.markDelivered(Instant.now());
        shipment.confirmReceipt(Instant.now(), false);
        Instant firstConfirmedAt = shipment.getReceiptConfirmedAt();

        // when
        shipment.confirmReceipt(Instant.now(), true);

        // then — 재요청은 무시되어 최초 확정 시각·auto 플래그가 그대로 유지된다
        assertThat(shipment.getReceiptConfirmedAt()).isEqualTo(firstConfirmedAt);
        assertThat(shipment.isReceiptAutoConfirmed()).isFalse();
    }

    @Test
    void 자동확정_처리하면_receiptAutoConfirmed가_true가_된다() {
        // given
        Shipment shipment = Shipment.create(1024L, 123L);
        shipment.registerShipment("CJ대한통운", "123456789012");
        shipment.markDelivered(Instant.now());

        // when
        shipment.confirmReceipt(Instant.now(), true);

        // then
        assertThat(shipment.isReceiptAutoConfirmed()).isTrue();
    }
}
