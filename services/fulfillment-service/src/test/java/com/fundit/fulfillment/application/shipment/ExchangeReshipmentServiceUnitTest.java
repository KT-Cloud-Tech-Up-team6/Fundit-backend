package com.fundit.fulfillment.application.shipment;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeReshipmentServiceUnitTest {

    private static final UUID FUNDING_ID = new UUID(0L, 1024L);
    private static final UUID PROJECT_ID = new UUID(0L, 123L);

    @Mock
    private ShipmentRepository shipmentRepository;

    private ExchangeReshipmentService exchangeReshipmentService;

    @BeforeEach
    void setUp() {
        exchangeReshipmentService = new ExchangeReshipmentService(shipmentRepository);
    }

    @Test
    void 재발송을_요청하면_PREPARING과_누적_횟수를_반환한다() {
        // given
        when(shipmentRepository.findByFundingIdForUpdate(FUNDING_ID)).thenReturn(Optional.of(delivered()));
        when(shipmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var result = exchangeReshipmentService.startReshipment(FUNDING_ID, 77L);

        // then
        assertThat(result.status()).isEqualTo(ShipmentStatus.PREPARING.name());
        assertThat(result.reshipmentCount()).isEqualTo(1);
    }

    @Test
    void 같은_교환_신청으로_재요청하면_저장하지_않는다() {
        // given — 이미 같은 refundRequestId로 재발송된 배송
        Shipment shipment = delivered();
        shipment.startReshipment(77L, Instant.now());
        when(shipmentRepository.findByFundingIdForUpdate(FUNDING_ID)).thenReturn(Optional.of(shipment));

        // when
        var result = exchangeReshipmentService.startReshipment(FUNDING_ID, 77L);

        // then
        assertThat(result.reshipmentCount()).isEqualTo(1);
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    void 발송_이력이_없으면_NOT_FOUND다() {
        // given
        when(shipmentRepository.findByFundingIdForUpdate(FUNDING_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> exchangeReshipmentService.startReshipment(FUNDING_ID, 77L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    private static Shipment delivered() {
        Shipment shipment = Shipment.create(FUNDING_ID, PROJECT_ID);
        shipment.registerShipment("CJ대한통운", "123456789012");
        shipment.markDelivered(Instant.now());
        return shipment;
    }
}
