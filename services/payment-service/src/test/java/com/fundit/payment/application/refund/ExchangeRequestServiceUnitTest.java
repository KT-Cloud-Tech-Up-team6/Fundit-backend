package com.fundit.payment.application.refund;

import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundRequestStatus;
import com.fundit.payment.domain.refund.RefundTriggerType;
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
class ExchangeRequestServiceUnitTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID FUNDING_ID = new UUID(0L, 1024L);
    private static final UUID SELLER_ID = UUID.randomUUID();

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
    void 본인_완료결제면_교환신청이_REQUESTED로_저장된다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-1", 89_000L, "주문", null, "idem");
        payment.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(new OrderFundingClient.FundingSnapshot(
                MEMBER_ID, SELLER_ID, "SUCCEEDED", 89_000L, "주문", null, FUNDING_ID, 0L, 0L));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> {
            RefundRequest saved = inv.getArgument(0);
            return saved.toBuilder().id(21L).build();
        });

        // when
        var result = exchangeRequestService.request(MEMBER_ID, FUNDING_ID, "사이즈를 잘못 선택했어요",
                List.of("https://cdn/a.jpg"));

        // then
        assertThat(result.refundId()).isEqualTo(21L);
        assertThat(result.status()).isEqualTo(RefundRequestStatus.REQUESTED.name());
        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getTriggerType()).isEqualTo(RefundTriggerType.EXCHANGE);
        assertThat(captor.getValue().getSellerId()).isEqualTo(SELLER_ID);
    }
}
