package com.fundit.payment.application.settlement;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import com.fundit.payment.domain.settlement.SettlementBatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceUnitExceptionTest {

    private static final UUID SELLER_ID = UUID.randomUUID();

    @Mock
    private SettlementBatchRepository settlementBatchRepository;
    @Mock
    private OrderSettlementAggregateClient orderSettlementAggregateClient;

    private SettlementQueryService settlementQueryService;

    @BeforeEach
    void setUp() {
        settlementQueryService = new SettlementQueryService(settlementBatchRepository, orderSettlementAggregateClient);
    }

    @Test
    void 배치가_없으면_NOT_FOUND다() {
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementQueryService.getDetail(SELLER_ID, 77L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        verifyNoInteractions(orderSettlementAggregateClient);
    }

    @Test
    void 타인_배치면_FORBIDDEN이다() {
        SettlementBatch batch = SettlementBatch.create(UUID.randomUUID(), SettlementBatchType.INTERIM,
                Instant.now(), Instant.now(), 100_000L, 3_000L, 0L, 0L, List.of());
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> settlementQueryService.getDetail(SELLER_ID, 77L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
        verifyNoInteractions(orderSettlementAggregateClient);
    }
}
