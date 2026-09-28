package com.fundit.payment.application.refund;

import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.application.notification.PaymentNotificationPublisher;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.domain.refund.RefundRequestRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefectRefundDecisionServiceUnitTest {

    private static final UUID FUNDING_ID = new UUID(0L, 1024L);
    private static final UUID SELLER_ID = UUID.randomUUID();

    @Mock
    private RefundRequestRepository refundRequestRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderFundingClient orderFundingClient;
    @Mock
    private RefundExecutionService refundExecutionService;
    @Mock
    private PaymentNotificationPublisher paymentNotificationPublisher;

    @Mock
    private ExchangeService exchangeService;

    private DefectRefundDecisionService defectRefundDecisionService;

    @BeforeEach
    void setUp() {
        defectRefundDecisionService = new DefectRefundDecisionService(refundRequestRepository, paymentRepository,
                orderFundingClient, refundExecutionService, exchangeService,
                paymentNotificationPublisher);
    }

    private RefundRequest defectRequest(UUID paymentId) {
        return RefundRequest.requestAfterShipment(RefundTriggerType.DEFECT, FUNDING_ID, paymentId, UUID.randomUUID(), "파손", List.of("url"));
    }

    @Test
    void 판매자_본인이_승인하면_전액취소가_실행된다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, UUID.randomUUID(), "fundit-order-1", 89_000L, "주문", null, "idem");
        payment.markCompleted("pay_key_1", "secret_1", PaymentMethod.CARD, null, Instant.now());
        RefundRequest refundRequest = defectRequest(payment.getId());

        when(refundRequestRepository.findById(1L)).thenReturn(Optional.of(refundRequest));
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(
                new OrderFundingClient.FundingSnapshot(UUID.randomUUID(), SELLER_ID, "GOAL_ACHIEVED", 89_000L, "주문", null,
                        FUNDING_ID, 0L, 0L));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(refundExecutionService.executeApprovedRefund(refundRequest, 89_000L, "하자환불 승인"))
                .thenReturn(new RefundExecutionService.RefundExecutionResult(1L, "COMPLETED", true));

        // when
        var result = defectRefundDecisionService.decide(SELLER_ID, 1L, true, null);

        // then
        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(refundExecutionService).executeApprovedRefund(refundRequest, 89_000L, "하자환불 승인");
    }

    @Test
    void 반품_승인은_반품비를_뺀_금액만_부분취소한다() {
        // given — 23,000원 결제, 반품비 5,000원 → 18,000원만 취소
        Payment payment = Payment.create(FUNDING_ID, UUID.randomUUID(), "fundit-order-2", 23_000L, "주문", null, "idem");
        payment.markCompleted("pay_key_2", "secret_2", PaymentMethod.CARD, null, Instant.now());
        RefundRequest refundRequest = RefundRequest.requestAfterShipment(RefundTriggerType.RETURN_CHANGE_OF_MIND,
                FUNDING_ID, payment.getId(), SELLER_ID, "[CHANGE_OF_MIND] 색상이 달라요", List.of());

        when(refundRequestRepository.findById(2L)).thenReturn(Optional.of(refundRequest));
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(
                new OrderFundingClient.FundingSnapshot(UUID.randomUUID(), SELLER_ID, "GOAL_ACHIEVED", 23_000L, "주문",
                        null, FUNDING_ID, 0L, 0L));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(refundExecutionService.executeApprovedRefund(refundRequest, 18_000L, "반품 승인(반품비 차감)"))
                .thenReturn(new RefundExecutionService.RefundExecutionResult(2L, "COMPLETED", false));

        // when
        var result = defectRefundDecisionService.decide(SELLER_ID, 2L, true, null);

        // then
        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(refundExecutionService).executeApprovedRefund(refundRequest, 18_000L, "반품 승인(반품비 차감)");
    }

    @Test
    void 판매자_본인이_반려하면_사유와_함께_REJECTED로_전환된다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, UUID.randomUUID(), "fundit-order-1", 89_000L, "주문", null, "idem");
        payment.markCompleted("pay_key_1", "secret_1", PaymentMethod.CARD, null, Instant.now());
        RefundRequest refundRequest = defectRequest(payment.getId());
        when(refundRequestRepository.findById(1L)).thenReturn(Optional.of(refundRequest));
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(
                new OrderFundingClient.FundingSnapshot(UUID.randomUUID(), SELLER_ID, "GOAL_ACHIEVED", 89_000L, "주문", null,
                        FUNDING_ID, 0L, 0L));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var result = defectRefundDecisionService.decide(SELLER_ID, 1L, false, "제품 이상 없음 확인됨");

        // then
        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(refundRequest.getRejectedReason()).isEqualTo("제품 이상 없음 확인됨");
        verify(paymentNotificationPublisher).publishRefundStatusChanged(
                new PaymentNotificationPublisher.RefundStatusChangedEvent(FUNDING_ID, payment.getMemberId(),
                        PaymentNotificationPublisher.RefundNotificationStatus.REJECTED));
    }
}
