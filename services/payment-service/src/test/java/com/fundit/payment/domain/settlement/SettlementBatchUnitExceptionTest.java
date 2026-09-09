package com.fundit.payment.domain.settlement;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementBatchUnitExceptionTest {

    private SettlementBatch pending() {
        return SettlementBatch.create(UUID.randomUUID(), SettlementBatchType.INTERIM,
                Instant.now(), Instant.now(), 100_000L, 3_000L, 0L, 0L, List.of());
    }

    @Test
    void 이미_지급된_배치는_이의신청으로_보류할_수_없다() {
        SettlementBatch batch = pending();
        batch.markPaid();

        assertThatThrownBy(batch::holdForDispute)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.CONFLICT));
    }

    @Test
    void 보류_상태는_지급_대상이_아니다() {
        SettlementBatch batch = pending();
        batch.holdForDispute();

        assertThatThrownBy(batch::markPaid)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.CONFLICT));
    }
}
