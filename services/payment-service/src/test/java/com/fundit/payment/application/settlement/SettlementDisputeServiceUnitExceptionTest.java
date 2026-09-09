package com.fundit.payment.application.settlement;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import com.fundit.payment.domain.settlement.SettlementBatchType;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementDisputeJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementDisputeServiceUnitExceptionTest {

    private static final UUID SELLER_ID = UUID.randomUUID();

    @Mock
    private SettlementBatchRepository settlementBatchRepository;
    @Mock
    private SettlementDisputeJpaRepository settlementDisputeJpaRepository;

    private SettlementDisputeService settlementDisputeService;

    @BeforeEach
    void setUp() {
        settlementDisputeService = new SettlementDisputeService(settlementBatchRepository, settlementDisputeJpaRepository);
    }

    @Test
    void 대상_배치가_없으면_NOT_FOUND_예외가_발생한다() {
        // given
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> settlementDisputeService.create(SELLER_ID, 77L, "사유", List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 본인_배치가_아니면_FORBIDDEN_예외가_발생한다() {
        // given
        SettlementBatch batch = SettlementBatch.create(UUID.randomUUID(), SettlementBatchType.INTERIM,
                        Instant.now(), Instant.now(), 100_000L, 3_000L, 0L, 0L, List.of())
                .toBuilder().createdAt(Instant.now()).build();
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.of(batch));

        // when & then
        assertThatThrownBy(() -> settlementDisputeService.create(SELLER_ID, 77L, "사유", List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 배치_생성일로부터_7일이_지나면_DISPUTE_PERIOD_EXPIRED_예외가_발생한다() {
        // given
        SettlementBatch batch = SettlementBatch.create(SELLER_ID, SettlementBatchType.INTERIM,
                        Instant.now(), Instant.now(), 100_000L, 3_000L, 0L, 0L, List.of())
                .toBuilder().createdAt(Instant.now().minus(Duration.ofDays(8))).build();
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.of(batch));

        // when & then
        assertThatThrownBy(() -> settlementDisputeService.create(SELLER_ID, 77L, "사유", List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.DISPUTE_PERIOD_EXPIRED));
    }
}
