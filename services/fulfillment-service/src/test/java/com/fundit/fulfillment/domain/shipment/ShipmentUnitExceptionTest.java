package com.fundit.fulfillment.domain.shipment;

import com.fundit.common.error.BusinessException;
import com.fundit.fulfillment.domain.FulfillmentErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShipmentUnitExceptionTest {

    @Test
    void 이미_발송된_건에_재등록하면_예외가_발생한다() {
        // given
        Shipment shipment = Shipment.create(1024L, 123L);
        shipment.registerShipment("CJ대한통운", "123456789012");

        // when & then
        assertThatThrownBy(() -> shipment.registerShipment("우체국택배", "999"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.ALREADY_SHIPPED));
    }

    @Test
    void 배송완료_전에_수령확인하면_예외가_발생한다() {
        // given — 아직 PREPARING 상태
        Shipment shipment = Shipment.create(1024L, 123L);

        // when & then
        assertThatThrownBy(() -> shipment.confirmReceipt(Instant.now(), false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.NOT_YET_DELIVERED));
    }

    @Test
    void 발송만_된_상태에서_수령확인하면_예외가_발생한다() {
        // given — SHIPPED까지만 진행, 아직 DELIVERED 아님
        Shipment shipment = Shipment.create(1024L, 123L);
        shipment.registerShipment("CJ대한통운", "123456789012");

        // when & then
        assertThatThrownBy(() -> shipment.confirmReceipt(Instant.now(), false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(FulfillmentErrorCode.NOT_YET_DELIVERED));
    }
}
