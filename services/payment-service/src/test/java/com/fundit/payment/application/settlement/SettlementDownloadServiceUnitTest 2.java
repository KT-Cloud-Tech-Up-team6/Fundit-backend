package com.fundit.payment.application.settlement;

import com.fundit.payment.application.settlement.OrderSettlementAggregateClient.LineItemAggregate;
import com.fundit.payment.application.settlement.SettlementQueryService.SettlementDetail;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * PAYMENT-010. 접근 권한 검증/NOT_FOUND·FORBIDDEN 전파는 위임 대상인
 * {@link SettlementQueryService#getDetail}이 이미 검증하므로 여기서는 CSV 변환만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class SettlementDownloadServiceUnitTest {

    private static final UUID SELLER_ID = UUID.randomUUID();

    @Mock
    private SettlementQueryService settlementQueryService;

    private SettlementDownloadService settlementDownloadService;

    @BeforeEach
    void setUp() {
        settlementDownloadService = new SettlementDownloadService(settlementQueryService);
    }

    @Test
    void 정산배치를_CSV로_변환한다() {
        // given
        SettlementBatch batch = SettlementBatch.create(SELLER_ID, SettlementBatchType.INTERIM,
                        Instant.EPOCH, Instant.EPOCH, 100_000L, 3_000L, 0L, 0L, List.of())
                .toBuilder().id(77L).build();
        SettlementDetail detail = new SettlementDetail(batch, List.of(new LineItemAggregate(5L, "리워드", "화이트", 2, 100_000L)));
        when(settlementQueryService.getDetail(SELLER_ID, 77L)).thenReturn(detail);

        // when
        byte[] csv = settlementDownloadService.download(SELLER_ID, 77L);

        // then
        String content = new String(csv, StandardCharsets.UTF_8);
        assertThat(content).contains("77,INTERIM,PENDING").contains("5,리워드,화이트,2,100000");
    }
}
