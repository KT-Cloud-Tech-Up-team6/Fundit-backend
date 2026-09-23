package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRequestServiceUnitExceptionTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID FUNDING_ID = new UUID(0L, 1024L);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRequestRepository refundRequestRepository;
    @Mock
    private OrderFundingClient orderFundingClient;

    private ExchangeRequestService exchangeRequestService;

    @BeforeEach
    void setUp() {
        exchangeRequestService = new ExchangeRequestService(paymentRepository, refundRequestRepository,
                orderFundingClient);
    }

    @Test
    void 완료된_결제가_없으면_NOT_FOUND다() {
        // given
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> exchangeRequestService.request(MEMBER_ID, FUNDING_ID, "사유", List.of("url")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
        verifyNoInteractions(refundRequestRepository);
    }

    @Test
    void 타인_결제면_FORBIDDEN이다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, UUID.randomUUID(), "fundit-1", 89_000L, "주문", null, "idem");
        payment.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> exchangeRequestService.request(MEMBER_ID, FUNDING_ID, "사유", List.of("url")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
        verifyNoInteractions(refundRequestRepository);
    }
}
