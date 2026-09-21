package com.fundit.payment.application.settlement;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.refund.ShippingStatusClient;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchItem;
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
    @Mock
    private ShippingStatusClient shippingStatusClient;

    private SettlementDisputeService settlementDisputeService;

    @BeforeEach
    void setUp() {
        settlementDisputeService = new SettlementDisputeService(settlementBatchRepository, settlementDisputeJpaRepository,
                shippingStatusClient);
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

    @Test
    void 최종정산은_배송완료일_14일후_7일이_지나면_DISPUTE_PERIOD_EXPIRED_예외가_발생한다() {
        // given — 배송완료 22일 전이라 14+7=21일 창을 이미 지났다(배치 생성일 기준이면 통과했을 케이스)
        SettlementBatch batch = SettlementBatch.create(SELLER_ID, SettlementBatchType.FINAL,
                        Instant.now(), Instant.now(), 100_000L, 3_000L, 0L, 0L,
                        List.of(SettlementBatchItem.of(1L, UUID.randomUUID(), 100_000L, 100_000L)))
                .toBuilder().createdAt(Instant.now().minus(Duration.ofDays(1))).build();
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.of(batch));
        when(shippingStatusClient.fetch(1L)).thenReturn(
                new ShippingStatusClient.ShippingStatus(true, false, Instant.now().minus(Duration.ofDays(22)), null));

        // when & then
        assertThatThrownBy(() -> settlementDisputeService.create(SELLER_ID, 77L, "사유", List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.DISPUTE_PERIOD_EXPIRED));
    }

    @Test
    void 상세조회시_이의신청이_없으면_NOT_FOUND_예외가_발생한다() {
        // given
        when(settlementDisputeJpaRepository.findById(9L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> settlementDisputeService.getDetail(SELLER_ID, 9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 상세조회시_본인_접수건이_아니면_FORBIDDEN_예외가_발생한다() {
        // given
        var dispute = com.fundit.payment.infrastructure.persistence.settlement.SettlementDisputeJpaEntity.builder()
                .id(9L).batchId(77L).sellerId(UUID.randomUUID()).reason("사유").status("RECEIVED").build();
        when(settlementDisputeJpaRepository.findById(9L)).thenReturn(Optional.of(dispute));

        // when & then
        assertThatThrownBy(() -> settlementDisputeService.getDetail(SELLER_ID, 9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 최종정산인데_배송완료일을_확인할_수_없으면_DEPENDENCY_FAILURE_예외가_발생한다() {
        // given — shipping.completed.v1로만 생성되는 배치가 배송완료일을 못 찾는 건 시스템 불변식 위반이라
        // 배치 생성일로 조용히 대체하지 않고 명시적으로 실패해야 한다.
        SettlementBatch batch = SettlementBatch.create(SELLER_ID, SettlementBatchType.FINAL,
                        Instant.now(), Instant.now(), 100_000L, 3_000L, 0L, 0L,
                        List.of(SettlementBatchItem.of(1L, UUID.randomUUID(), 100_000L, 100_000L)))
                .toBuilder().createdAt(Instant.now()).build();
        when(settlementBatchRepository.findById(77L)).thenReturn(Optional.of(batch));
        when(shippingStatusClient.fetch(1L)).thenReturn(new ShippingStatusClient.ShippingStatus(true, false, null, null));

        // when & then
        assertThatThrownBy(() -> settlementDisputeService.create(SELLER_ID, 77L, "사유", List.of()))
                .isInstanceOf(DependencyFailureException.class);
    }
}
