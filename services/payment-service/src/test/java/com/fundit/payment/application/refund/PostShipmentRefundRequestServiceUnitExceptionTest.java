package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.ErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.domain.PaymentErrorCode;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundTriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostShipmentRefundRequestServiceUnitExceptionTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID FUNDING_ID = new UUID(0L, 1024L);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private RefundRequestRepository refundRequestRepository;
    @Mock
    private OrderFundingClient orderFundingClient;
    @Mock
    private ShippingStatusClient shippingStatusClient;

    private PostShipmentRefundRequestService postShipmentRefundRequestService;

    @BeforeEach
    void setUp() {
        postShipmentRefundRequestService = new PostShipmentRefundRequestService(paymentRepository,
                refundRequestRepository, orderFundingClient, shippingStatusClient);
    }

    @Test
    void 완료된_결제가_없으면_NOT_FOUND다() {
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> request(RefundTriggerType.DEFECT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertErrorCode(e, CommonErrorCode.NOT_FOUND));
        verifyNoInteractions(refundRequestRepository);
    }

    @Test
    void 타인_결제면_FORBIDDEN이다() {
        givenCompletedPaymentOf(UUID.randomUUID(), 89_000L);

        assertThatThrownBy(() -> request(RefundTriggerType.DEFECT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertErrorCode(e, CommonErrorCode.FORBIDDEN));
        verifyNoInteractions(refundRequestRepository);
    }

    @Test
    void 배송완료_전이면_NOT_DELIVERED다() {
        givenCompletedPaymentOf(MEMBER_ID, 89_000L);
        when(shippingStatusClient.fetch(FUNDING_ID)).thenReturn(new ShippingStatusClient.ShippingStatus(
                true, false, null, null));

        assertThatThrownBy(() -> request(RefundTriggerType.RETURN_CHANGE_OF_MIND))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertErrorCode(e, PaymentErrorCode.NOT_DELIVERED));
        verifyNoInteractions(refundRequestRepository);
    }

    @Test
    void 수령_후_7일이_지났으면_RETURN_PERIOD_EXPIRED다() {
        givenCompletedPaymentOf(MEMBER_ID, 89_000L);
        givenDeliveredDaysAgo(8);

        assertThatThrownBy(() -> request(RefundTriggerType.RETURN_CHANGE_OF_MIND))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertErrorCode(e, PaymentErrorCode.RETURN_PERIOD_EXPIRED));
        verifyNoInteractions(refundRequestRepository);
    }

    @Test
    void 하자환불_신청도_기한이_지나면_차단된다() {
        givenCompletedPaymentOf(MEMBER_ID, 89_000L);
        givenDeliveredDaysAgo(10);

        assertThatThrownBy(() -> request(RefundTriggerType.DEFECT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertErrorCode(e, PaymentErrorCode.RETURN_PERIOD_EXPIRED));
    }

    @Test
    void 진행중인_신청이_있으면_REFUND_ALREADY_REQUESTED다() {
        givenCompletedPaymentOf(MEMBER_ID, 89_000L);
        givenDeliveredDaysAgo(2);
        when(refundRequestRepository.existsUnresolvedPostShipmentRequest(FUNDING_ID)).thenReturn(true);

        assertThatThrownBy(() -> request(RefundTriggerType.RETURN_CHANGE_OF_MIND))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertErrorCode(e, PaymentErrorCode.REFUND_ALREADY_REQUESTED));
        verify(refundRequestRepository).existsUnresolvedPostShipmentRequest(FUNDING_ID);
        verifyNoInteractions(orderFundingClient);
    }

    @Test
    void 결제액이_반품비_이하면_RETURN_FEE_EXCEEDS_AMOUNT다() {
        givenCompletedPaymentOf(MEMBER_ID, 5_000L);
        givenDeliveredDaysAgo(2);

        assertThatThrownBy(() -> request(RefundTriggerType.RETURN_CHANGE_OF_MIND))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertErrorCode(e, PaymentErrorCode.RETURN_FEE_EXCEEDS_AMOUNT));
        verifyNoInteractions(orderFundingClient);
    }

    @Test
    void 하자환불은_증빙이_없으면_EVIDENCE_REQUIRED다() {
        givenCompletedPaymentOf(MEMBER_ID, 89_000L);
        givenDeliveredDaysAgo(2);
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(new OrderFundingClient.FundingSnapshot(
                MEMBER_ID, UUID.randomUUID(), "SUCCEEDED", 89_000L, "주문", null, FUNDING_ID, 0L, 0L));

        assertThatThrownBy(() -> postShipmentRefundRequestService.request(MEMBER_ID, FUNDING_ID,
                RefundTriggerType.DEFECT, "파손", List.of()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertErrorCode(e, PaymentErrorCode.EVIDENCE_REQUIRED));
    }

    /**
     * exists 검사를 통과한 뒤 경합에서 진 쪽 — DB 유니크 인덱스(V8)가 INSERT를 거절하면 같은
     * 409로 번역해야 한다. 중복 접수가 두 건 승인되면 반품비 차감 부분취소가 두 번 실행된다.
     */
    @Test
    void 저장_시점에_중복이_감지되면_REFUND_ALREADY_REQUESTED다() {
        givenCompletedPaymentOf(MEMBER_ID, 23_000L);
        givenDeliveredDaysAgo(2);
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(new OrderFundingClient.FundingSnapshot(
                MEMBER_ID, UUID.randomUUID(), "SUCCEEDED", 23_000L, "주문", null, FUNDING_ID, 0L, 0L));
        when(refundRequestRepository.save(any())).thenThrow(
                new DataIntegrityViolationException("uq_refund_requests_unresolved_post_shipment"));

        assertThatThrownBy(() -> request(RefundTriggerType.RETURN_CHANGE_OF_MIND))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertErrorCode(e, PaymentErrorCode.REFUND_ALREADY_REQUESTED));
    }

    private void request(RefundTriggerType triggerType) {
        postShipmentRefundRequestService.request(MEMBER_ID, FUNDING_ID, triggerType, "사유", List.of("url"));
    }

    private void givenCompletedPaymentOf(UUID ownerId, long amount) {
        Payment payment = Payment.create(FUNDING_ID, ownerId, "fundit-1", amount, "주문", null, "idem");
        payment.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
    }

    private void givenDeliveredDaysAgo(int days) {
        when(shippingStatusClient.fetch(FUNDING_ID)).thenReturn(new ShippingStatusClient.ShippingStatus(
                true, false, Instant.now().minus(Duration.ofDays(days)), null));
    }

    private void assertErrorCode(Throwable e, ErrorCode expected) {
        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(expected);
    }
}
