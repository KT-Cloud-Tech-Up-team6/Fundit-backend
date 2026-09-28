package com.fundit.payment.application.payment;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.application.settlement.SettlementHoldService;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceUnitExceptionTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID FUNDING_ID = new UUID(0L, 1024L);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TossPaymentsClient tossPaymentsClient;
    @Mock
    private PaymentEventPublisher paymentEventPublisher;
    @Mock
    private SettlementHoldService settlementHoldService;
    @Mock
    private PaymentFailureRecorder paymentFailureRecorder;
    @Mock
    private ExchangeFeePaymentListener exchangeFeePaymentListener;

    private PaymentConfirmService paymentConfirmService;

    @BeforeEach
    void setUp() {
        paymentConfirmService = new PaymentConfirmService(paymentRepository, tossPaymentsClient,
                paymentEventPublisher, settlementHoldService, paymentFailureRecorder,
                exchangeFeePaymentListener);
    }

    @Test
    void 금액이_스냅샷과_다르면_토스_호출_없이_예외가_발생한다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 1_000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH));
        org.mockito.Mockito.verifyNoInteractions(tossPaymentsClient);
    }

    @Test
    void 본인_결제건이_아니면_FORBIDDEN_예외가_발생한다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, UUID.randomUUID(), "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));

        // when & then
        assertThatThrownBy(() -> paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 89_000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 토스_세션만료_응답이면_별도_트랜잭션으로_FAILED_기록을_위임하고_PAYMENT_EXPIRED_예외가_발생한다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.confirm("pay_key_1", "fundit-order-1", 89_000L))
                .thenThrow(new TossApiException(TossApiException.NOT_FOUND_PAYMENT_SESSION, "만료"));

        // when & then
        assertThatThrownBy(() -> paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 89_000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_EXPIRED));
        org.mockito.Mockito.verify(paymentFailureRecorder).recordFailure(payment);
    }

    @Test
    void 토스_승인_실패면_별도_트랜잭션으로_FAILED_기록을_위임하고_PG_CONFIRM_FAILED_예외가_발생한다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.confirm("pay_key_1", "fundit-order-1", 89_000L))
                .thenThrow(new TossApiException("EXCEED_MAX_AUTH_COUNT", "인증 횟수 초과"));

        // when & then
        assertThatThrownBy(() -> paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 89_000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.PG_CONFIRM_FAILED));
        org.mockito.Mockito.verify(paymentFailureRecorder).recordFailure(payment);
    }

    @Test
    void 토스_승인_실패면_토스_오류_코드를_detail로_내린다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.confirm("pay_key_1", "fundit-order-1", 89_000L))
                .thenThrow(new TossApiException("REJECT_CARD_COMPANY", "카드사 거절"));

        // when & then — FE가 거절 사유별 안내를 분기할 수 있다
        assertThatThrownBy(() -> paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 89_000L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getDetail())
                        .isEqualTo(java.util.Map.of("tossErrorCode", "REJECT_CARD_COMPANY")));
    }

    @Test
    void 결과_불명이고_토스_조회로도_승인이_확인되지_않으면_FAILED로_굳히지_않고_예외를_그대로_던진다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.confirm("pay_key_1", "fundit-order-1", 89_000L))
                .thenThrow(new com.fundit.common.error.DependencyFailureException(new RuntimeException("5xx")));
        when(tossPaymentsClient.lookup("pay_key_1"))
                .thenThrow(new com.fundit.common.error.DependencyFailureException(new RuntimeException("lookup fail")));

        // when & then — 트랜잭션이 롤백돼 결제는 PENDING으로 남고 재확정할 수 있다
        assertThatThrownBy(() -> paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 89_000L))
                .isInstanceOf(com.fundit.common.error.DependencyFailureException.class);
        org.mockito.Mockito.verifyNoInteractions(paymentFailureRecorder);
    }

    @Test
    void 이미_처리된_결제라는데_조회로_승인을_확인하지_못하면_FAILED로_굳히지_않는다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.confirm("pay_key_1", "fundit-order-1", 89_000L))
                .thenThrow(new TossApiException(TossApiException.ALREADY_PROCESSED_PAYMENT, "이미 처리된 결제"));
        when(tossPaymentsClient.lookup("pay_key_1"))
                .thenThrow(new com.fundit.common.error.DependencyFailureException(new RuntimeException("lookup fail")));

        // when & then — 토스에선 승인됐을 수 있어 결과 불명(503)으로 두고 재확정을 허용한다
        assertThatThrownBy(() -> paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 89_000L))
                .isInstanceOf(com.fundit.common.error.DependencyFailureException.class);
        org.mockito.Mockito.verifyNoInteractions(paymentFailureRecorder);
    }

    @Test
    void 토스_조회_결과의_금액이_다르면_승인으로_인정하지_않는다() {
        // given
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-order-1", 89_000L, "테스트 주문", null, "idem");
        when(paymentRepository.findByPgOrderId("fundit-order-1")).thenReturn(Optional.of(payment));
        when(tossPaymentsClient.confirm("pay_key_1", "fundit-order-1", 89_000L))
                .thenThrow(new com.fundit.common.error.DependencyFailureException(new RuntimeException("timeout")));
        when(tossPaymentsClient.lookup("pay_key_1")).thenReturn(new TossPaymentsClient.TossPaymentLookup("DONE",
                new TossPaymentsClient.TossPaymentResult("pay_key_1", "fundit-order-1", null, "카드", null,
                        java.time.Instant.now(), 1_000L)));

        // when & then
        assertThatThrownBy(() -> paymentConfirmService.confirm(MEMBER_ID, "pay_key_1", "fundit-order-1", 89_000L))
                .isInstanceOf(com.fundit.common.error.DependencyFailureException.class);
    }
}
