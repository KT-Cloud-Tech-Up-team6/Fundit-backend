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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostShipmentRefundRequestServiceUnitTest {

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID FUNDING_ID = new UUID(0L, 1024L);
    private static final UUID SELLER_ID = UUID.randomUUID();

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
    void 본인_완료결제면_하자환불_신청이_REQUESTED로_저장된다() {
        // given
        Payment payment = givenCompletedPayment(89_000L);
        givenDeliveredDaysAgo(3);
        givenSellerAndSavedId(11L);

        // when
        var result = postShipmentRefundRequestService.request(MEMBER_ID, FUNDING_ID, RefundTriggerType.DEFECT,
                "[DAMAGED] 파손", List.of("https://cdn/a.jpg"));

        // then
        assertThat(result.refundId()).isEqualTo(11L);
        assertThat(result.status()).isEqualTo(RefundRequestStatus.REQUESTED.name());
        assertThat(result.returnShippingFee()).isZero();
        assertThat(result.estimatedRefundAmount()).isEqualTo(89_000L);
        RefundRequest saved = captureSaved();
        assertThat(saved.getFundingId()).isEqualTo(FUNDING_ID);
        assertThat(saved.getPaymentId()).isEqualTo(payment.getId());
        assertThat(saved.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(saved.getTriggerType()).isEqualTo(RefundTriggerType.DEFECT);
    }

    @Test
    void 교환신청도_같은_경로로_REQUESTED로_저장된다() {
        // given
        givenCompletedPayment(89_000L);
        givenDeliveredDaysAgo(1);
        givenSellerAndSavedId(21L);

        // when
        var result = postShipmentRefundRequestService.request(MEMBER_ID, FUNDING_ID, RefundTriggerType.EXCHANGE,
                "사이즈를 잘못 선택했어요", List.of());

        // then — 단순변심 교환은 증빙이 없어도 접수된다
        assertThat(result.refundId()).isEqualTo(21L);
        assertThat(captureSaved().getTriggerType()).isEqualTo(RefundTriggerType.EXCHANGE);
    }

    @Test
    void 단순변심_반품은_반품비를_뺀_예상환불액을_함께_돌려준다() {
        // given — 23,000원 결제 → 반품비 5,000원 차감 시 18,000원
        givenCompletedPayment(23_000L);
        givenDeliveredDaysAgo(6);
        givenSellerAndSavedId(31L);

        // when
        var result = postShipmentRefundRequestService.request(MEMBER_ID, FUNDING_ID,
                RefundTriggerType.RETURN_CHANGE_OF_MIND, "[CHANGE_OF_MIND] 색상이 달라요", List.of());

        // then
        assertThat(result.paymentAmount()).isEqualTo(23_000L);
        assertThat(result.returnShippingFee()).isEqualTo(5_000L);
        assertThat(result.estimatedRefundAmount()).isEqualTo(18_000L);
        RefundRequest saved = captureSaved();
        assertThat(saved.getTriggerType()).isEqualTo(RefundTriggerType.RETURN_CHANGE_OF_MIND);
        assertThat(saved.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
        assertThat(saved.getEvidenceUrls()).isEmpty();
    }

    @Test
    void 수령_후_7일_경계_직전이면_접수된다() {
        // given
        givenCompletedPayment(23_000L);
        when(shippingStatusClient.fetch(FUNDING_ID)).thenReturn(new ShippingStatusClient.ShippingStatus(
                true, false, Instant.now().minus(Duration.ofDays(7)).plusSeconds(60), null));
        givenSellerAndSavedId(41L);

        // when
        var result = postShipmentRefundRequestService.request(MEMBER_ID, FUNDING_ID,
                RefundTriggerType.RETURN_CHANGE_OF_MIND, "[WRONG_OPTION] 옵션 실수", null);

        // then
        assertThat(result.refundId()).isEqualTo(41L);
    }

    private Payment givenCompletedPayment(long amount) {
        Payment payment = Payment.create(FUNDING_ID, MEMBER_ID, "fundit-1", amount, "주문", null, "idem");
        payment.markCompleted("pay_key", "secret", PaymentMethod.CARD, null, Instant.now());
        when(paymentRepository.findCompletedByFundingId(FUNDING_ID)).thenReturn(Optional.of(payment));
        return payment;
    }

    private void givenDeliveredDaysAgo(int days) {
        when(shippingStatusClient.fetch(FUNDING_ID)).thenReturn(new ShippingStatusClient.ShippingStatus(
                true, false, Instant.now().minus(Duration.ofDays(days)), null));
    }

    private void givenSellerAndSavedId(long savedId) {
        when(orderFundingClient.fetch(FUNDING_ID)).thenReturn(new OrderFundingClient.FundingSnapshot(
                MEMBER_ID, SELLER_ID, "SUCCEEDED", 89_000L, "주문", null, FUNDING_ID, 0L, 0L));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> {
            RefundRequest saved = inv.getArgument(0);
            return saved.toBuilder().id(savedId).build();
        });
    }

    private RefundRequest captureSaved() {
        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundRequestRepository).save(captor.capture());
        return captor.getValue();
    }
}
