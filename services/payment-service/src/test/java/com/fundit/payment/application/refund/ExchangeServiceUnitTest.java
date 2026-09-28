package com.fundit.payment.application.refund;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.payment.application.reshipment.ExchangeReshipmentClient;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.refund.ExchangeReason;
import com.fundit.payment.domain.refund.RefundReasonTag;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestRepository;
import com.fundit.payment.domain.refund.RefundRequestStatus;
import com.fundit.payment.domain.refund.RefundTriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceUnitTest {

    private static final UUID ORDER_ID = new UUID(0L, 1024L);
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final Long REFUND_ID = 77L;

    @Mock
    private RefundRequestRepository refundRequestRepository;
    @Mock
    private ExchangeReshipmentClient exchangeReshipmentClient;

    private ExchangeService exchangeService;

    @BeforeEach
    void setUp() {
        exchangeService = new ExchangeService(refundRequestRepository, exchangeReshipmentClient);
    }

    @Nested
    class 판매자_승인 {

        @Test
        void 구매자_귀책이면_교환비_결제를_기다린다() {
            // given
            RefundRequest request = exchangeRequest(ExchangeReason.WRONG_OPTION);
            when(refundRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            ExchangeService.ExchangeApprovalResult result = exchangeService.approve(request);

            // then — 결제 전에는 재발송을 요청하지 않는다
            assertThat(result.status()).isEqualTo(RefundRequestStatus.APPROVED.name());
            assertThat(result.additionalPaymentAmount()).isEqualTo(5_000L);
            verifyNoInteractions(exchangeReshipmentClient);
        }

        @Test
        void 판매자_귀책이면_추가_결제_없이_재발송을_요청한다() {
            // given
            RefundRequest request = exchangeRequest(ExchangeReason.WRONG_DELIVERY);
            when(refundRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(exchangeReshipmentClient.request(ORDER_ID, REFUND_ID))
                    .thenReturn(new ExchangeReshipmentClient.ReshipmentResult("PREPARING", 1));

            // when
            ExchangeService.ExchangeApprovalResult result = exchangeService.approve(request);

            // then
            assertThat(result.status()).isEqualTo(RefundRequestStatus.PROCESSING.name());
            assertThat(result.additionalPaymentAmount()).isZero();
            verify(exchangeReshipmentClient).request(ORDER_ID, REFUND_ID);
        }
    }

    @Test
    void 교환비_결제가_승인되면_재발송을_요청한다() {
        // given
        RefundRequest request = exchangeRequest(ExchangeReason.CHANGE_OF_MIND);
        request.approveExchangeAwaitingFee();
        Payment feePayment = Payment.createExchangeFee(ORDER_ID, UUID.randomUUID(), "fundit-fee", 5_000L,
                "교환 배송비", REFUND_ID, "idem");
        when(refundRequestRepository.findById(REFUND_ID)).thenReturn(Optional.of(request));
        when(refundRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(exchangeReshipmentClient.request(ORDER_ID, REFUND_ID))
                .thenReturn(new ExchangeReshipmentClient.ReshipmentResult("PREPARING", 1));

        // when
        exchangeService.onExchangeFeePaid(feePayment);

        // then
        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PROCESSING);
        verify(exchangeReshipmentClient).request(ORDER_ID, REFUND_ID);
    }

    @Nested
    class 재발송_발송_이벤트 {

        @Test
        void 재발송_진행중인_교환이_있으면_완료로_전이한다() {
            // given
            RefundRequest request = exchangeRequest(ExchangeReason.CHANGE_OF_MIND);
            request.approveExchangeAwaitingFee();
            request.startExchangeReshipment();
            when(refundRequestRepository.findReshippingExchangeByFundingId(ORDER_ID)).thenReturn(Optional.of(request));
            when(refundRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            exchangeService.onReshipmentShipped(ORDER_ID, REFUND_ID);

            // then
            assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.COMPLETED);
            assertThat(request.getProcessedAt()).isNotNull();
        }

        @Test
        void 교환_건이_없으면_아무것도_하지_않는다() {
            // given — 일반 주문의 발송 이벤트(재발송 식별자는 있지만 진행 중인 교환이 없다)
            when(refundRequestRepository.findReshippingExchangeByFundingId(ORDER_ID)).thenReturn(Optional.empty());

            // when
            exchangeService.onReshipmentShipped(ORDER_ID, REFUND_ID);

            // then
            verify(refundRequestRepository).findReshippingExchangeByFundingId(ORDER_ID);
        }

        /** Kafka는 at-least-once — 최초 발송 이벤트가 나중에 재전달돼도 교환을 완료시키면 안 된다. */
        @Test
        void 재발송_식별자가_없는_최초_발송_이벤트는_무시한다() {
            // when
            exchangeService.onReshipmentShipped(ORDER_ID, null);

            // then — 조회조차 하지 않는다
            verifyNoInteractions(refundRequestRepository);
        }

        @Test
        void 다른_교환_건의_재발송_식별자면_무시한다() {
            // given
            RefundRequest request = exchangeRequest(ExchangeReason.CHANGE_OF_MIND);
            request.approveExchangeAwaitingFee();
            request.startExchangeReshipment();
            when(refundRequestRepository.findReshippingExchangeByFundingId(ORDER_ID)).thenReturn(Optional.of(request));

            // when — 이전 교환 건(99L)의 재발송 이벤트가 늦게 도착
            exchangeService.onReshipmentShipped(ORDER_ID, 99L);

            // then
            assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PROCESSING);
            verify(refundRequestRepository, never()).save(any());
        }
    }

    /** 저장된 신청처럼 id를 가진 교환 건 — 재발송 식별자 대조에 id가 필요하다. */
    private static RefundRequest exchangeRequest(ExchangeReason reason) {
        return RefundRequest.requestAfterShipment(RefundTriggerType.EXCHANGE, ORDER_ID, PAYMENT_ID,
                        UUID.randomUUID(), RefundReasonTag.format(reason, "상세"), List.of())
                .toBuilder().id(REFUND_ID).build();
    }

    /** 결제는 이미 승인됐으므로 fulfillment 호출 실패가 결제/상태 전이를 되돌리면 안 된다. */
    @Test
    void 교환비_결제_후_재발송_호출이_실패해도_상태는_PROCESSING으로_남는다() {
        // given
        RefundRequest request = exchangeRequest(ExchangeReason.CHANGE_OF_MIND);
        request.approveExchangeAwaitingFee();
        Payment feePayment = Payment.createExchangeFee(ORDER_ID, UUID.randomUUID(), "fundit-fee", 5_000L,
                "교환 배송비", REFUND_ID, "idem");
        when(refundRequestRepository.findById(REFUND_ID)).thenReturn(Optional.of(request));
        when(refundRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(exchangeReshipmentClient.request(ORDER_ID, REFUND_ID))
                .thenThrow(new DependencyFailureException(new IllegalStateException("fulfillment 장애")));

        // when
        exchangeService.onExchangeFeePaid(feePayment);

        // then
        assertThat(request.getStatus()).isEqualTo(RefundRequestStatus.PROCESSING);
    }

    @Nested
    class 재발송_요청_재시도 {

        @Test
        void 요청이_성공하면_시각이_기록되어_대상에서_빠진다() {
            // given
            RefundRequest request = exchangeRequest(ExchangeReason.CHANGE_OF_MIND);
            request.approveExchangeAwaitingFee();
            request.startExchangeReshipment();
            when(refundRequestRepository.findExchangesAwaitingReshipmentRequest(50)).thenReturn(List.of(request));
            when(refundRequestRepository.findById(REFUND_ID)).thenReturn(Optional.of(request));
            when(refundRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(exchangeReshipmentClient.request(ORDER_ID, REFUND_ID))
                    .thenReturn(new ExchangeReshipmentClient.ReshipmentResult("PREPARING", 1));

            // when
            int requested = exchangeService.retryPendingReshipmentRequests(50);

            // then
            assertThat(requested).isEqualTo(1);
            assertThat(request.getReshipmentRequestedAt()).isNotNull();
        }

        @Test
        void 요청이_또_실패하면_시각이_비어_다음_주기에_다시_대상이_된다() {
            // given
            RefundRequest request = exchangeRequest(ExchangeReason.CHANGE_OF_MIND);
            request.approveExchangeAwaitingFee();
            request.startExchangeReshipment();
            when(refundRequestRepository.findExchangesAwaitingReshipmentRequest(50)).thenReturn(List.of(request));
            when(refundRequestRepository.findById(REFUND_ID)).thenReturn(Optional.of(request));
            when(exchangeReshipmentClient.request(ORDER_ID, REFUND_ID))
                    .thenThrow(new DependencyFailureException(new IllegalStateException("fulfillment 장애")));

            // when
            int requested = exchangeService.retryPendingReshipmentRequests(50);

            // then
            assertThat(requested).isZero();
            assertThat(request.getReshipmentRequestedAt()).isNull();
        }
    }
}
