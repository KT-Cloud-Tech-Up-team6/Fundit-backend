package com.fundit.payment.application.refund;

import com.fundit.payment.application.event.PaymentEventPublisher;
import com.fundit.payment.application.payment.TossPaymentsClient;
import com.fundit.payment.application.settlement.SettlementHoldService;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentMethod;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.payment.PaymentStatus;
import com.fundit.payment.domain.refund.RefundRequestStatus;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaEntity;
import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaRepository;
import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaEntity;
import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaRepository;
import com.fundit.payment.infrastructure.persistence.refund.RefundRequestJpaEntity;
import com.fundit.payment.infrastructure.persistence.refund.RefundRequestJpaRepository;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementHoldJpaEntity;
import com.fundit.payment.infrastructure.persistence.settlement.SettlementHoldJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 회귀 테스트(#182) — 토스 취소는 성공했는데 확정 트랜잭션이 실패해도 "취소 요청됨"이 실제로 커밋돼 남고,
 * 대사 배치가 토스 조회로 환불 내역·정산 보류 해제·RefundCompleted까지 복구하는지 검증한다.
 *
 * <p>{@code PaymentConfirmServiceFailureCommitIntegrationTest}와 같은 이유로 클래스에 {@code @Transactional}을
 * 붙이지 않는다 — 테스트 트랜잭션과 묶이면 실제로 커밋됐는지와 무관하게 값이 보여 버그를 못 잡는다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        "payment.encryption.key=Oz9qy5geAzhDXyHfZdFB3WPwVH8/sx/uD2j5rX5DGkY=",
        "toss.payments.secret-key=test_sk_dummy",
        "media.s3.bucket=unused", "media.s3.region=ap-northeast-2",
        "media.public-base-url=https://cdn.test/media/",
        "refund-cancel-reconcile.worker-enabled=false"
})
class RefundExecutionServiceRecoveryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private RefundExecutionService refundExecutionService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private SettlementHoldService settlementHoldService;
    @Autowired
    private RefundRequestJpaRepository refundRequestJpaRepository;
    @Autowired
    private PaymentCancellationJpaRepository paymentCancellationJpaRepository;
    @Autowired
    private SettlementHoldJpaRepository settlementHoldJpaRepository;
    @Autowired
    private PaymentEventOutboxJpaRepository outboxRepository;

    @MockitoBean
    private TossPaymentsClient tossPaymentsClient;
    @MockitoSpyBean
    private PaymentEventPublisher paymentEventPublisher;

    @Test
    void 토스_취소_후_확정이_실패해도_대사_배치가_환불_내역과_보류_해제와_이벤트까지_복구한다() {
        // given — 완료 결제 + 정산 보류
        Payment payment = Payment.create(new UUID(0L, 9101L), UUID.randomUUID(), "fundit-order-recover-1",
                77_000L, "테스트 주문", List.of(), "idem-recover-1");
        payment.markCompleted("pay_key_recover_1", "secret", PaymentMethod.CARD, null, Instant.now());
        Payment saved = paymentRepository.save(payment);
        settlementHoldService.openHold(saved.getId(), null, saved.getAmount());

        TossPaymentsClient.TossCancelResult canceled = new TossPaymentsClient.TossCancelResult(
                "tx_recover_1", Instant.now(), 77_000L);
        when(tossPaymentsClient.cancel("pay_key_recover_1", 77_000L, "사유")).thenReturn(canceled);
        when(tossPaymentsClient.lookup("pay_key_recover_1")).thenReturn(new TossPaymentsClient.TossPaymentLookup(
                "CANCELED", new TossPaymentsClient.TossPaymentResult("pay_key_recover_1", "fundit-order-recover-1",
                null, "카드", null, Instant.now(), 77_000L), List.of(canceled)));
        // 첫 확정에서만 아웃박스 적재가 실패한다
        doThrow(new IllegalStateException("outbox down")).doCallRealMethod()
                .when(paymentEventPublisher).publishRefundCompleted(any());

        // when — 토스 취소는 성공했지만 확정 트랜잭션이 롤백된다
        assertThatThrownBy(() -> refundExecutionService.executeFullRefund(saved.getFundingId(),
                RefundTriggerType.SIMPLE_CHANGE_OF_MIND, "사유"))
                .isInstanceOf(IllegalStateException.class);

        // then — 취소 요청은 커밋돼 남아 있고, 확정 기록은 하나도 없다
        RefundRequestJpaEntity requested = refundRequestJpaRepository.findAll().stream()
                .filter(r -> r.getPaymentId().equals(saved.getId())).findFirst().orElseThrow();
        assertThat(requested.getStatus()).isEqualTo(RefundRequestStatus.PROCESSING.name());
        assertThat(requested.getCancelAmount()).isEqualTo(77_000L);
        assertThat(paymentRepository.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(paymentCancellationJpaRepository.findByPaymentId(saved.getId())).isEmpty();
        assertThat(settlementHoldJpaRepository.findByPaymentId(saved.getId()).orElseThrow().isHolding()).isTrue();

        // when — 대사 배치
        int resolved = refundExecutionService.reconcileCancelsRequestedBefore(Instant.now().plusSeconds(1), 50);

        // then — 토스를 다시 부르지 않고 조회한 취소로 전부 복구된다
        assertThat(resolved).isEqualTo(1);
        assertThat(paymentRepository.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        RefundRequestJpaEntity completed = refundRequestJpaRepository.findById(requested.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(RefundRequestStatus.COMPLETED.name());
        assertThat(completed.getIsFullRefund()).isTrue();
        assertThat(paymentCancellationJpaRepository.findByPaymentId(saved.getId()))
                .extracting(PaymentCancellationJpaEntity::getPgTransactionKey).containsExactly("tx_recover_1");
        assertThat(settlementHoldJpaRepository.findByPaymentId(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(SettlementHoldJpaEntity.STATUS_RELEASED_TO_REFUND);
        assertThat(outboxRepository.findAll()).filteredOn(o -> o.getPaymentId().equals(saved.getId()))
                .extracting(PaymentEventOutboxJpaEntity::getEventType).containsExactly("RefundCompleted");
        verify(tossPaymentsClient).cancel("pay_key_recover_1", 77_000L, "사유");
    }
}
