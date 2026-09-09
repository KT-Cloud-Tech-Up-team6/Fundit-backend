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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementDownloadServiceUnitExceptionTest {

    private static final UUID SELLER_ID = UUID.randomUUID();

    @Mock
    private SettlementBatchRepository settlementBatchRepository;

    private SettlementDownloadService settlementDownloadService;

    @BeforeEach
    void setUp() {
        settlementDownloadService = new SettlementDownloadService(settlementBatchRepository);
    }

    @Test
    void 본인_배치여도_파일생성_미구현이라_503이다() {
        SettlementBatch batch = SettlementBatch.create(SELLER_ID, SettlementBatchType.INTERIM,
                Instant.now(), Instant.now(), 100_000L, 3_000L, 0L, 0L, List.of());
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> settlementDownloadService.assertDownloadable(SELLER_ID, 77L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void 배치가_없으면_NOT_FOUND다() {
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementDownloadService.assertDownloadable(SELLER_ID, 77L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 타인_배치면_FORBIDDEN이다() {
        SettlementBatch batch = SettlementBatch.create(UUID.randomUUID(), SettlementBatchType.INTERIM,
                Instant.now(), Instant.now(), 100_000L, 3_000L, 0L, 0L, List.of());
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> settlementDownloadService.assertDownloadable(SELLER_ID, 77L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }
}
