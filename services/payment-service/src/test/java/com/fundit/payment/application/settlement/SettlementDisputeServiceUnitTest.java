package com.fundit.payment.application.settlement;

import com.fundit.payment.application.refund.ShippingStatusClient;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchItem;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import com.fundit.payment.domain.settlement.SettlementBatchStatus;
import com.fundit.payment.domain.settlement.SettlementBatchType;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementDisputeJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementDisputeJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementDisputeServiceUnitTest {

    private static final UUID SELLER_ID = UUID.randomUUID();

    @Mock
    private SettlementBatchRepository settlementBatchRepository;
    @Mock
    private SettlementDisputeJpaRepository settlementDisputeJpaRepository;
    @Mock
    private ShippingStatusClient shippingStatusClient;

    private SettlementDisputeService settlementDisputeService;

    @BeforeEach
    void setUp() {
        settlementDisputeService = new SettlementDisputeService(settlementBatchRepository, settlementDisputeJpaRepository,
                shippingStatusClient);
    }

    private SettlementBatch freshBatch() {
        return SettlementBatch.create(SELLER_ID, SettlementBatchType.INTERIM, Instant.now(), Instant.now(),
                        100_000L, 3_000L, 0L, 0L, List.of())
                .toBuilder()
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void 기간내_이의신청을_접수하면_배치가_보류상태로_전환된다() {
        // given
        SettlementBatch batch = freshBatch();
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.of(batch));
        when(settlementBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(settlementDisputeJpaRepository.save(any())).thenReturn(SettlementDisputeJpaEntity.builder()
                .id(1L)
                .batchId(77L)
                .sellerId(SELLER_ID)
                .reason("정산 금액 산출 내역이 다릅니다.")
                .status("RECEIVED")
                .build());

        // when
        var result = settlementDisputeService.create(SELLER_ID, 77L, "정산 금액 산출 내역이 다릅니다.",
                List.of("https://cdn/evidence1.pdf"));

        // then
        assertThat(batch.getStatus()).isEqualTo(SettlementBatchStatus.ON_HOLD);
        assertThat(result.status()).isEqualTo("RECEIVED");
        ArgumentCaptor<SettlementBatch> captor = ArgumentCaptor.forClass(SettlementBatch.class);
        verify(settlementBatchRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SettlementBatchStatus.ON_HOLD);
    }

    @Test
    void 최종정산은_마지막_배송완료일_14일후_7일_이내면_접수된다() {
        // given — 두 펀딩 중 더 늦게 배송완료된 쪽(6일 전)을 기준으로 14+7=21일 창 안에 있다
        Instant earlierDelivery = Instant.now().minus(Duration.ofDays(20));
        Instant latestDelivery = Instant.now().minus(Duration.ofDays(6));
        SettlementBatch batch = SettlementBatch.create(SELLER_ID, SettlementBatchType.FINAL, Instant.now(), Instant.now(),
                        100_000L, 3_000L, 0L, 0L,
                        List.of(SettlementBatchItem.of(1L, UUID.randomUUID(), 50_000L, 40_000L),
                                SettlementBatchItem.of(2L, UUID.randomUUID(), 50_000L, 40_000L)))
                .toBuilder().createdAt(Instant.now().minus(Duration.ofDays(30))).build();
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.of(batch));
        when(shippingStatusClient.fetch(1L)).thenReturn(new ShippingStatusClient.ShippingStatus(true, false, earlierDelivery, null));
        when(shippingStatusClient.fetch(2L)).thenReturn(new ShippingStatusClient.ShippingStatus(true, false, latestDelivery, null));
        when(settlementBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(settlementDisputeJpaRepository.save(any())).thenReturn(SettlementDisputeJpaEntity.builder()
                .id(2L).batchId(77L).sellerId(SELLER_ID).reason("사유").status("RECEIVED").build());

        // when
        var result = settlementDisputeService.create(SELLER_ID, 77L, "사유", List.of());

        // then — 오래된 배송완료일(20일 전)이 아니라 최신(6일 전) 기준으로 계산돼 아직 기간 내다
        assertThat(result.status()).isEqualTo("RECEIVED");
        assertThat(batch.getStatus()).isEqualTo(SettlementBatchStatus.ON_HOLD);
    }

    @Test
    void 판매자_이의신청목록을_요약으로_변환한다() {
        // given
        PageRequest pageable = PageRequest.of(0, 20);
        var dispute = SettlementDisputeJpaEntity.builder()
                .id(9L).batchId(77L).sellerId(SELLER_ID).reason("사유").status("RECEIVED").build();
        when(settlementDisputeJpaRepository.findBySellerIdOrderByIdDesc(SELLER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(dispute), pageable, 1));

        // when
        var page = settlementDisputeService.listForSeller(SELLER_ID, pageable);

        // then
        assertThat(page.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.disputeId()).isEqualTo(9L);
            assertThat(summary.settlementBatchId()).isEqualTo(77L);
            assertThat(summary.status()).isEqualTo("RECEIVED");
        });
    }

    @Test
    void 본인_이의신청_상세를_조회한다() {
        // given
        var dispute = SettlementDisputeJpaEntity.builder()
                .id(9L).batchId(77L).sellerId(SELLER_ID).reason("사유").status("RECEIVED").build();
        when(settlementDisputeJpaRepository.findById(9L)).thenReturn(Optional.of(dispute));

        // when
        var summary = settlementDisputeService.getDetail(SELLER_ID, 9L);

        // then
        assertThat(summary.disputeId()).isEqualTo(9L);
        assertThat(summary.settlementBatchId()).isEqualTo(77L);
    }
}
