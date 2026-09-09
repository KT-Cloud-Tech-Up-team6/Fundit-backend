package com.fundit.payment.application.settlement;

import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchItem;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceUnitTest {

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
    void 본인_배치면_라인아이템을_합성해_반환한다() {
        // given
        UUID paymentId = UUID.randomUUID();
        SettlementBatch batch = SettlementBatch.create(SELLER_ID, SettlementBatchType.INTERIM,
                        Instant.now(), Instant.now(), 100_000L, 3_000L, 0L, 0L,
                        List.of(new SettlementBatchItem(1L, 1024L, paymentId, 100_000L)))
                .toBuilder().id(77L).build();
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.of(batch));
        when(orderSettlementAggregateClient.fetchLineItems(1024L)).thenReturn(List.of(
                new OrderSettlementAggregateClient.LineItemAggregate(5L, "리워드", "화이트", 2, 100_000L)));

        // when
        var detail = settlementQueryService.getDetail(SELLER_ID, 77L);

        // then
        assertThat(detail.batch().getId()).isEqualTo(77L);
        assertThat(detail.lineItems()).singleElement().satisfies(item -> {
            assertThat(item.rewardId()).isEqualTo(5L);
            assertThat(item.rewardName()).isEqualTo("리워드");
            assertThat(item.amount()).isEqualTo(100_000L);
        });
    }
}
