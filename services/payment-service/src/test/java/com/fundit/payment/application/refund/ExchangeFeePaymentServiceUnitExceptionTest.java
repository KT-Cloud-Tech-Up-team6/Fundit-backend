package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.payment.PgOrderIdIssuer;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.ExchangeReason;
import com.fundit.payment.domain.refund.RefundReasonTag;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeFeePaymentServiceUnitExceptionTest {

    private static final UUID ORDER_ID = new UUID(0L, 1024L);
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final Long REFUND_ID = 55L;

    @Mock
    private RefundRequestRepository refundRequestRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PgOrderIdIssuer pgOrderIdIssuer;

    private ExchangeFeePaymentService exchangeFeePaymentService;

    @BeforeEach
    void setUp() {
        exchangeFeePaymentService = new ExchangeFeePaymentService(refundRequestRepository, paymentRepository,
                pgOrderIdIssuer);
    }

    @Test
    void 판매자_승인_전이면_EXCHANGE_NOT_APPROVED다() {
        // given — 접수(REQUESTED) 상태
        givenExchangeRequest(ExchangeReason.CHANGE_OF_MIND, false);

        // when & then
        assertThatThrownBy(() -> exchangeFeePaymentService.create(MEMBER_ID, REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.EXCHANGE_NOT_APPROVED));
    }

    @Test
    void 판매자_귀책_교환이면_EXCHANGE_FEE_NOT_REQUIRED다() {
        // given — 승인됐지만 구매자 부담이 0원인 사유
        givenExchangeRequest(ExchangeReason.DEFECTIVE, true);

        // when & then
        assertThatThrownBy(() -> exchangeFeePaymentService.create(MEMBER_ID, REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.EXCHANGE_FEE_NOT_REQUIRED));
    }

    @Test
    void 이미_결제된_교환이면_EXCHANGE_FEE_ALREADY_PAID다() {
        // given
        givenExchangeRequest(ExchangeReason.CHANGE_OF_MIND, true);
        Payment paid = Payment.createExchangeFee(ORDER_ID, MEMBER_ID, "fundit-fee", 5_000L, "교환 배송비", REFUND_ID,
                "idem");
        paid.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findLatestExchangeFeeByRefundRequestId(REFUND_ID)).thenReturn(Optional.of(paid));

        // when & then
        assertThatThrownBy(() -> exchangeFeePaymentService.create(MEMBER_ID, REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.EXCHANGE_FEE_ALREADY_PAID));
    }

    @Test
    void 타인의_교환_신청이면_FORBIDDEN이다() {
        // given — 원 결제 소유자가 다른 회원
        givenExchangeRequest(ExchangeReason.CHANGE_OF_MIND, true, UUID.randomUUID());

        // when & then
        assertThatThrownBy(() -> exchangeFeePaymentService.create(MEMBER_ID, REFUND_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    private void givenExchangeRequest(ExchangeReason reason, boolean approved) {
        givenExchangeRequest(reason, approved, MEMBER_ID);
    }

    private void givenExchangeRequest(ExchangeReason reason, boolean approved, UUID ownerId) {
        Payment rewardPayment = Payment.create(ORDER_ID, ownerId, "fundit-1", 23_000L, "주문", null, "idem-reward");
        RefundRequest request = RefundRequest.requestAfterShipment(RefundTriggerType.EXCHANGE, ORDER_ID,
                rewardPayment.getId(), UUID.randomUUID(), RefundReasonTag.format(reason, "상세"), List.of());
        if (approved) {
            request.approveExchangeAwaitingFee();
        }
        when(refundRequestRepository.findById(REFUND_ID)).thenReturn(Optional.of(request));
        when(paymentRepository.findById(rewardPayment.getId())).thenReturn(Optional.of(rewardPayment));
    }
}
