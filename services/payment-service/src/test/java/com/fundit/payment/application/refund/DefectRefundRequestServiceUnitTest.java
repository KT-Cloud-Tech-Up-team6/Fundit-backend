package com.fundit.payment.application.refund;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class DefectRefundRequestServiceUnitTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final Long FUNDING_ID = 1024L;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRequestRepository refundRequestRepository;

    private DefectRefundRequestService defectRefundRequestService;

    @BeforeEach
    void setUp() {
        defectRefundRequestService = new DefectRefundRequestService(paymentRepository, refundRequestRepository);
    }

    @Test
    void 본인_완료결제면_하자환불_신청이_REQUESTED로_저장된다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-1", 89_000L, "주문", null, "idem");
        payment.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> {
            RefundRequest saved = inv.getArgument(0);
            return saved.toBuilder().id(11L).build();
        });

        // when
        var result = defectRefundRequestService.request(MEMBER_ID, FUNDING_ID, "[DAMAGED] 파손",
                List.of("https://cdn/a.jpg"));

        // then
        assertThat(result.refundId()).isEqualTo(11L);
        assertThat(result.status()).isEqualTo(RefundRequestStatus.REQUESTED.name());
        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getFundingId()).isEqualTo(FUNDING_ID);
        assertThat(captor.getValue().getPaymentId()).isEqualTo(payment.getId());
    }
}
