package com.fundit.payment.application.settlement;

import com.fundit.payment.infrastructure.persistence.settlement.SettlementHoldJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementHoldJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementHoldServiceUnitTest {

    @Mock
    private SettlementHoldJpaRepository settlementHoldJpaRepository;

    private SettlementHoldService settlementHoldService;

    @BeforeEach
    void setUp() {
        settlementHoldService = new SettlementHoldService(settlementHoldJpaRepository);
    }

    @Test
    void 결제완료시_보류를_연다() {
        // given
        UUID paymentId = UUID.randomUUID();

        // when
        settlementHoldService.openHold(paymentId, 1024L, 89_000L);

        // then
        ArgumentCaptor<SettlementHoldJpaEntity> captor = ArgumentCaptor.forClass(SettlementHoldJpaEntity.class);
        verify(settlementHoldJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentId()).isEqualTo(paymentId);
        assertThat(captor.getValue().getFundingId()).isEqualTo(1024L);
        assertThat(captor.getValue().getHoldAmount()).isEqualTo(89_000L);
    }

    @Test
    void HOLDING_상태면_환불_해제로_전환한다() {
        // given
        UUID paymentId = UUID.randomUUID();
        SettlementHoldJpaEntity hold = SettlementHoldJpaEntity.builder()
                .paymentId(paymentId)
                .fundingId(1024L)
                .holdAmount(89_000L)
                .status(SettlementHoldJpaEntity.STATUS_HOLDING)
                .build();
        when(settlementHoldJpaRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(hold));

        // when
        settlementHoldService.releaseToRefund(paymentId);

        // then
        assertThat(hold.getStatus()).isEqualTo(SettlementHoldJpaEntity.STATUS_RELEASED_TO_REFUND);
        assertThat(hold.getReleasedAt()).isNotNull();
        verify(settlementHoldJpaRepository).save(hold);
    }

    @Test
    void HOLDING_상태면_정산_해제로_전환한다() {
        // given
        UUID paymentId = UUID.randomUUID();
        SettlementHoldJpaEntity hold = SettlementHoldJpaEntity.builder()
                .paymentId(paymentId)
                .fundingId(1024L)
                .holdAmount(89_000L)
                .status(SettlementHoldJpaEntity.STATUS_HOLDING)
                .build();
        when(settlementHoldJpaRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(hold));

        // when
        settlementHoldService.releaseToSettlement(paymentId);

        // then
        assertThat(hold.getStatus()).isEqualTo(SettlementHoldJpaEntity.STATUS_RELEASED_TO_SETTLEMENT);
        verify(settlementHoldJpaRepository).save(hold);
    }

    @Test
    void 이미_해제된_보류는_다시_저장하지_않는다() {
        // given
        UUID paymentId = UUID.randomUUID();
        SettlementHoldJpaEntity hold = SettlementHoldJpaEntity.builder()
                .paymentId(paymentId)
                .fundingId(1024L)
                .holdAmount(89_000L)
                .status(SettlementHoldJpaEntity.STATUS_RELEASED_TO_REFUND)
                .build();
        when(settlementHoldJpaRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(hold));

        // when
        settlementHoldService.releaseToRefund(paymentId);

        // then
        verify(settlementHoldJpaRepository, never()).save(hold);
    }
}
