package com.fundit.payment.application.refund;

import com.fundit.payment.domain.refund.RefundTriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceUnitTest {

    @Mock
    private RefundExecutionService refundExecutionService;

    private PaymentReconciliationService paymentReconciliationService;

    @BeforeEach
    void setUp() {
        paymentReconciliationService = new PaymentReconciliationService(refundExecutionService);
    }

    @Test
    void 재고만료_충돌_이벤트면_시스템_자동환불을_실행한다() {
        // given
        var event = new PaymentReconciliationListener.PaymentReconciliationRequiredEvent(1024L, UUID.randomUUID());

        // when
        paymentReconciliationService.onPaymentReconciliationRequired(event);

        // then
        verify(refundExecutionService).executeFullRefund(1024L, RefundTriggerType.SYSTEM_RECONCILIATION,
                "재고 확보 실패로 인한 시스템 자동 환불");
    }
}
