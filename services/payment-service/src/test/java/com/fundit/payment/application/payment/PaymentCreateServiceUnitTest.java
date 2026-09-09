package com.fundit.payment.application.payment;

import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCreateServiceUnitTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final Long FUNDING_ID = 1024L;

    @Mock
    private OrderFundingClient orderFundingClient;
    @Mock
    private PaymentRepository paymentRepository;

    private PaymentCreateService paymentCreateService;

    @BeforeEach
    void setUp() {
        paymentCreateService = new PaymentCreateService(orderFundingClient, paymentRepository);
    }

    @Test
    void 대상_주문이_PENDING이면_결제시도가_생성된다() {
        // given
        when(paymentRepository.findPendingByFundingId(FUNDING_ID)).thenReturn(Optional.empty());
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(
                new OrderFundingClient.FundingSnapshot(MEMBER_ID, UUID.randomUUID(), "PENDING", 89_000L, "테스트 주문", null));
        when(paymentRepository.existsByPgOrderId(any())).thenReturn(false);
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var result = paymentCreateService.create(MEMBER_ID, FUNDING_ID);

        // then
        assertThat(result.amount()).isEqualTo(89_000L);
        assertThat(result.orderName()).isEqualTo("테스트 주문");
        assertThat(result.pgOrderId()).startsWith("fundit-");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getFundingId()).isEqualTo(FUNDING_ID);
    }

    @Test
    void 이미_PENDING_시도가_있으면_재사용한다() {
        // given
        Payment existing = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-existing", 50_000L, "기존 주문", null, "idem");
        when(paymentRepository.findPendingByFundingId(FUNDING_ID)).thenReturn(Optional.of(existing));

        // when
        var result = paymentCreateService.create(MEMBER_ID, FUNDING_ID);

        // then
        assertThat(result.pgOrderId()).isEqualTo("fundit-existing");
        assertThat(result.amount()).isEqualTo(50_000L);
    }
}
