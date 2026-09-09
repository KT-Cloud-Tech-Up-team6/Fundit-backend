package com.fundit.payment.application.settlement;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import com.fundit.payment.domain.settlement.SettlementBatchType;
import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaEntity;
import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaRepository;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementScheduleJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementScheduleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementBatchGenerationServiceUnitTest {

    private static final UUID SELLER_ID = UUID.randomUUID();

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentCancellationJpaRepository paymentCancellationJpaRepository;
    @Mock
    private OrderSettlementAggregateClient orderSettlementAggregateClient;
    @Mock
    private SettlementBatchRepository settlementBatchRepository;
    @Mock
    private SettlementHoldService settlementHoldService;
    @Mock
    private SettlementScheduleJpaRepository settlementScheduleJpaRepository;

    private SettlementBatchGenerationService settlementBatchGenerationService;

    @BeforeEach
    void setUp() {
        settlementBatchGenerationService = new SettlementBatchGenerationService(paymentRepository,
                paymentCancellationJpaRepository, orderSettlementAggregateClient, settlementBatchRepository,
                settlementHoldService, settlementScheduleJpaRepository);
    }

    private Payment completedPayment(Long fundingId, long amount) {
        Payment payment = Payment.create(fundingId, UUID.randomUUID(), "fundit-" + fundingId, amount, "주문", null, "idem");
        payment.markCompleted("pay_key_" + fundingId, "secret", PaymentMethod.CARD, null, Instant.now());
        return payment;
    }

    private SettlementScheduleJpaEntity scheduleEntry(Long fundingId) {
        return SettlementScheduleJpaEntity.builder()
                .fundingId(fundingId)
                .projectId(10L)
                .sellerId(SELLER_ID)
                .batchType(SettlementScheduleJpaEntity.TYPE_INTERIM)
                .dueAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void 두건을_합쳐_배치를_생성하고_수수료3퍼센트를_계산한다() {
        // given
        Payment payment1 = completedPayment(1L, 100_000L);
        Payment payment2 = completedPayment(2L, 200_000L);
        SettlementScheduleJpaEntity entry1 = scheduleEntry(1L);
        SettlementScheduleJpaEntity entry2 = scheduleEntry(2L);

        when(paymentRepository.findCompletedOrCancelledByFundingId(1L)).thenReturn(Optional.of(payment1));
        when(paymentRepository.findCompletedOrCancelledByFundingId(2L)).thenReturn(Optional.of(payment2));
        when(paymentCancellationJpaRepository.findByPaymentId(any())).thenReturn(List.of());
        when(orderSettlementAggregateClient.fetchMakerCouponDeductionAmount(any())).thenReturn(0L);

        // when
        settlementBatchGenerationService.generate(SELLER_ID, SettlementBatchType.INTERIM, List.of(entry1, entry2));

        // then
        ArgumentCaptor<SettlementBatch> captor = ArgumentCaptor.forClass(SettlementBatch.class);
        verify(settlementBatchRepository).save(captor.capture());
        SettlementBatch saved = captor.getValue();
        assertThat(saved.getGrossAmount()).isEqualTo(300_000L);
        assertThat(saved.getPlatformFeeAmount()).isEqualTo(9_000L); // 300,000 * 3%
        assertThat(saved.getTotalAmount()).isEqualTo(291_000L);
        assertThat(saved.getItems()).hasSize(2);

        verify(settlementHoldService).releaseToSettlement(payment1.getId());
        verify(settlementHoldService).releaseToSettlement(payment2.getId());
        verify(settlementScheduleJpaRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void 환불차감액이_있으면_반영된다() {
        // given
        Payment payment = completedPayment(1L, 100_000L);
        SettlementScheduleJpaEntity entry = scheduleEntry(1L);
        when(paymentRepository.findCompletedOrCancelledByFundingId(1L)).thenReturn(Optional.of(payment));
        when(paymentCancellationJpaRepository.findByPaymentId(payment.getId())).thenReturn(List.of(
                PaymentCancellationJpaEntity.builder()
                        .paymentId(payment.getId())
                        .pgTransactionKey("tx-1")
                        .cancelAmount(20_000L)
                        .cancelReason("반품비 차감")
                        .build()));
        when(orderSettlementAggregateClient.fetchMakerCouponDeductionAmount(1L)).thenReturn(5_000L);

        // when
        settlementBatchGenerationService.generate(SELLER_ID, SettlementBatchType.INTERIM, List.of(entry));

        // then
        ArgumentCaptor<SettlementBatch> captor = ArgumentCaptor.forClass(SettlementBatch.class);
        verify(settlementBatchRepository).save(captor.capture());
        SettlementBatch saved = captor.getValue();
        assertThat(saved.getRefundDeductionAmount()).isEqualTo(20_000L);
        assertThat(saved.getCouponDeductionAmount()).isEqualTo(5_000L);
    }

    @Test
    void 대상_결제가_없는_건은_건너뛰고_아무_결제도_없으면_배치를_만들지_않는다() {
        // given
        SettlementScheduleJpaEntity entry = scheduleEntry(1L);
        when(paymentRepository.findCompletedOrCancelledByFundingId(1L)).thenReturn(Optional.empty());

        // when
        settlementBatchGenerationService.generate(SELLER_ID, SettlementBatchType.INTERIM, List.of(entry));

        // then
        verify(settlementBatchRepository, never()).save(any());
        verify(settlementScheduleJpaRepository).save(entry);
    }
}
