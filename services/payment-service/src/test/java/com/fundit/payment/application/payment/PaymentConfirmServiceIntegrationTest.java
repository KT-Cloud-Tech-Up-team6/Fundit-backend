package com.fundit.payment.application.payment;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.payment.PaymentStatus;
import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaEntity;
import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * PAYMENT-002 — 결제 승인 성공 시 Payment 상태 변경과 payment_event_outbox 적재가 실제로
 * 같은 트랜잭션에 들어가는지 검증한다(payment-service CLAUDE.md "결제 승인 성공 처리와 아웃박스
 * 적재를 별도 트랜잭션으로 분리하지 말 것"). TossPaymentsClient는 외부 PG라 목으로 대체한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        "payment.encryption.key=Oz9qy5geAzhDXyHfZdFB3WPwVH8/sx/uD2j5rX5DGkY=",
        "toss.payments.secret-key=test_sk_dummy",
        "media.s3.bucket=unused", "media.s3.region=ap-northeast-2"
})
@Transactional
class PaymentConfirmServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private PaymentConfirmService paymentConfirmService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentEventOutboxJpaRepository outboxRepository;

    @MockitoBean
    private TossPaymentsClient tossPaymentsClient;

    @Test
    void 승인에_성공하면_결제완료와_아웃박스_적재가_같은_트랜잭션에_들어간다() {
        // given
        Payment payment = Payment.create(new UUID(0L, 1024L), java.util.UUID.randomUUID(), "fundit-order-1",
                89_000L, "테스트 주문", List.of(7L), "idem-1");
        Payment saved = paymentRepository.save(payment);
        when(tossPaymentsClient.confirm(anyString(), anyString(), anyLong())).thenReturn(
                new TossPaymentsClient.TossPaymentResult("pay_key_1", "fundit-order-1", "secret_1", "카드",
                        null, Instant.now(), 89_000L));

        // when
        paymentConfirmService.confirm(saved.getMemberId(), "pay_key_1", "fundit-order-1", 89_000L);

        // then — Payment는 COMPLETED로
        Payment reloaded = paymentRepository.findByPgOrderId("fundit-order-1").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        // then — 같은 트랜잭션 안에서 아웃박스에도 PaymentCompleted가 남는다
        var pending = outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10));
        assertThat(pending).extracting(PaymentEventOutboxJpaEntity::getEventType)
                .contains(PaymentEventOutboxJpaEntity.TYPE_PAYMENT_COMPLETED);
        assertThat(pending).extracting(PaymentEventOutboxJpaEntity::getFundingId).contains(saved.getFundingId());
    }

    @Test
    void 승인에_실패하면_결제는_FAILED로_남고_아웃박스에는_아무것도_남지_않는다() {
        // given
        Payment payment = Payment.create(new UUID(0L, 2048L), java.util.UUID.randomUUID(), "fundit-order-2",
                50_000L, "테스트 주문", null, "idem-2");
        Payment saved = paymentRepository.save(payment);
        when(tossPaymentsClient.confirm(anyString(), anyString(), anyLong()))
                .thenThrow(new TossApiException("REJECT_CARD_COMPANY", "한도초과"));

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.fundit.common.error.BusinessException.class,
                () -> paymentConfirmService.confirm(saved.getMemberId(), "pay_key_2", "fundit-order-2", 50_000L));

        Payment reloaded = paymentRepository.findByPgOrderId("fundit-order-2").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.FAILED);
        var pending = outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10));
        assertThat(pending).extracting(PaymentEventOutboxJpaEntity::getFundingId).doesNotContain(saved.getFundingId());
    }
}
