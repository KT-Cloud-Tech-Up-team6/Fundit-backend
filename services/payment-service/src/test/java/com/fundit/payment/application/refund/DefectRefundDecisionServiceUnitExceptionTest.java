package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefectRefundDecisionServiceUnitExceptionTest {

    private static final Long FUNDING_ID = 1024L;

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

    @Test
    void 대상_신청건이_없으면_NOT_FOUND_예외가_발생한다() {
        // given
        when(refundRequestRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> defectRefundDecisionService.decide(UUID.randomUUID(), 1L, true, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    void 하자환불_신청이_아니면_INVALID_INPUT_예외가_발생한다() {
        // given
        RefundRequest simpleChangeRequest = RefundRequest.completeImmediately(
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, FUNDING_ID, UUID.randomUUID(), true);
        when(refundRequestRepository.findById(1L)).thenReturn(Optional.of(simpleChangeRequest));

        // when & then
        assertThatThrownBy(() -> defectRefundDecisionService.decide(UUID.randomUUID(), 1L, true, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    void 타_판매자가_결정하려하면_FORBIDDEN_예외가_발생한다() {
        // given
        RefundRequest refundRequest = RefundRequest.requestDefect(FUNDING_ID, UUID.randomUUID(), "파손", List.of("url"));
        when(refundRequestRepository.findById(1L)).thenReturn(Optional.of(refundRequest));
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(
                new OrderFundingClient.FundingSnapshot(UUID.randomUUID(), UUID.randomUUID(), "GOAL_ACHIEVED", 89_000L,
                        "주문", null));

        // when & then
        assertThatThrownBy(() -> defectRefundDecisionService.decide(UUID.randomUUID(), 1L, true, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }
}
