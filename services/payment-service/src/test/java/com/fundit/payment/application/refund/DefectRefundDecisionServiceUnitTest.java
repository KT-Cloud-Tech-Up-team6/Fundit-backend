package com.fundit.payment.application.refund;

import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundRequest;
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

    private static final Long FUNDING_ID = 1024L;
    private static final UUID SELLER_ID = UUID.randomUUID();

    @Mock
    private RefundRequestRepository refundRequestRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderFundingClient orderFundingClient;
    @Mock
    private RefundExecutionService refundExecutionService;

    private DefectRefundDecisionService defectRefundDecisionService;

    @BeforeEach
    void setUp() {
        defectRefundDecisionService = new DefectRefundDecisionService(refundRequestRepository, paymentRepository,
                orderFundingClient, refundExecutionService);
    }

    private RefundRequest defectRequest(UUID paymentId) {
        return RefundRequest.requestDefect(FUNDING_ID, paymentId, "파손", List.of("url"));
    }

    @Test
    void 판매자_본인이_승인하면_전액취소가_실행된다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, UUID.randomUUID(), "fundit-order-1", 89_000L, "주문", null, "idem");
        payment.markCompleted("pay_key_1", "secret_1", PaymentMethod.CARD, null, Instant.now());
        RefundRequest refundRequest = defectRequest(payment.getId());

        when(refundRequestRepository.findById(1L)).thenReturn(Optional.of(refundRequest));
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(
                new OrderFundingClient.FundingSnapshot(UUID.randomUUID(), SELLER_ID, "GOAL_ACHIEVED", 89_000L, "주문", null));
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
    void 판매자_본인이_반려하면_사유와_함께_REJECTED로_전환된다() {
        // given
        RefundRequest refundRequest = defectRequest(UUID.randomUUID());
        when(refundRequestRepository.findById(1L)).thenReturn(Optional.of(refundRequest));
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(
                new OrderFundingClient.FundingSnapshot(UUID.randomUUID(), SELLER_ID, "GOAL_ACHIEVED", 89_000L, "주문", null));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var result = defectRefundDecisionService.decide(SELLER_ID, 1L, false, "제품 이상 없음 확인됨");

        // then
        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(refundRequest.getRejectedReason()).isEqualTo("제품 이상 없음 확인됨");
    }
}
