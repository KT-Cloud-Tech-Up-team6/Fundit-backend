package com.fundit.payment.infrastructure.persistence.refund;

import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaEntity;
import com.fundit.payment.infrastructure.persistence.payment.PaymentCancellationJpaRepository;
import com.fundit.payment.infrastructure.persistence.payment.PaymentJpaEntity;
import com.fundit.payment.infrastructure.persistence.payment.PaymentJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * statuses 파라미터에 null을 바인딩해도(진행여부 필터 미적용) {@code IN} 절 때문에
 * 예외 없이 전체가 반환되는지 검증 — Hibernate가 IN(:param)에 바인딩된 null 컬렉션을
 * 항상 지원하는지는 버전에 따라 갈릴 수 있어 mock이 아닌 실제 DB로 확인한다.
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
class RefundRequestJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private RefundRequestJpaRepository refundRequestJpaRepository;
    @Autowired
    private PaymentJpaRepository paymentJpaRepository;
    @Autowired
    private PaymentCancellationJpaRepository paymentCancellationJpaRepository;

    @Test
    void statuses가_null이면_예외없이_전체_조회된다() {
        // given
        UUID memberId = UUID.randomUUID();
        Instant now = Instant.now();
        PaymentJpaEntity payment = paymentJpaRepository.save(PaymentJpaEntity.builder()
                .id(UUID.randomUUID()).memberId(memberId).pgOrderId("order-1").amount(10_000L)
                .orderName("테스트").couponIssuanceIds(List.of()).status("COMPLETED")
                .idempotencyKey(UUID.randomUUID().toString()).createdAt(now).updatedAt(now).build());
        refundRequestJpaRepository.save(RefundRequestJpaEntity.builder()
                .paymentId(payment.getId()).triggerType("SIMPLE_CHANGE_OF_MIND").status("COMPLETED")
                .requestedAt(now).build());

        // when
        var page = refundRequestJpaRepository.findSummariesByMemberId(
                memberId, null, null, PageRequest.of(0, 20));

        // then
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    /** V7 — trigger_type CHECK 제약에 신규 두 값이 포함됐는지는 실제 DB에만 물어볼 수 있다. */
    @Test
    void 반품과_교환_trigger_type이_저장된다() {
        // given
        PaymentJpaEntity payment = givenCompletedPayment(UUID.randomUUID(), 23_000L);

        // when & then — 제약 위반이면 save에서 예외가 난다
        assertThat(saveRequest(payment, "RETURN_CHANGE_OF_MIND", "REQUESTED").getId()).isNotNull();
        assertThat(saveRequest(payment, "EXCHANGE", "REQUESTED").getId()).isNotNull();
    }

    @Test
    void 목록의_amount는_실제_취소금액이고_취소이력이_없으면_결제원금이다() {
        // given — 23,000원 결제 중 반품비 5,000원을 뺀 18,000원만 취소된 반품 건
        UUID memberId = UUID.randomUUID();
        PaymentJpaEntity payment = givenCompletedPayment(memberId, 23_000L);
        RefundRequestJpaEntity refunded = saveRequest(payment, "RETURN_CHANGE_OF_MIND", "COMPLETED");
        paymentCancellationJpaRepository.save(PaymentCancellationJpaEntity.builder()
                .paymentId(payment.getId()).refundRequestId(refunded.getId())
                .pgTransactionKey("tx-" + UUID.randomUUID()).cancelAmount(18_000L)
                .cancelReason("반품 승인(반품비 차감)").canceledAt(Instant.now()).build());
        saveRequest(payment, "DEFECT", "REQUESTED");

        // when
        var page = refundRequestJpaRepository.findSummariesByMemberId(memberId, null, null, PageRequest.of(0, 20));

        // then — 취소 이력이 있는 건은 실제 취소액, 신청 중인 건은 결제 원금으로 폴백
        assertThat(page.getContent()).extracting("triggerType", "amount")
                .containsExactlyInAnyOrder(tuple("RETURN_CHANGE_OF_MIND", 18_000L), tuple("DEFECT", 23_000L));
    }

    @Test
    void 미처리_발송후_신청이_있으면_중복_접수를_막는다() {
        // given
        UUID fundingId = UUID.randomUUID();
        PaymentJpaEntity payment = givenCompletedPayment(UUID.randomUUID(), 23_000L);
        refundRequestJpaRepository.save(RefundRequestJpaEntity.builder()
                .paymentId(payment.getId()).fundingOrderId(fundingId).triggerType("RETURN_CHANGE_OF_MIND")
                .status("REQUESTED").requestedAt(Instant.now()).build());

        // when & then
        assertThat(refundRequestJpaRepository.existsByFundingOrderIdAndTriggerTypeInAndStatusIn(fundingId,
                List.of("DEFECT", "EXCHANGE", "RETURN_CHANGE_OF_MIND"), List.of("REQUESTED", "UNDER_REVIEW")))
                .isTrue();
        assertThat(refundRequestJpaRepository.existsByFundingOrderIdAndTriggerTypeInAndStatusIn(UUID.randomUUID(),
                List.of("DEFECT", "EXCHANGE", "RETURN_CHANGE_OF_MIND"), List.of("REQUESTED", "UNDER_REVIEW")))
                .isFalse();
    }

    private PaymentJpaEntity givenCompletedPayment(UUID memberId, long amount) {
        Instant now = Instant.now();
        return paymentJpaRepository.save(PaymentJpaEntity.builder()
                .id(UUID.randomUUID()).memberId(memberId).pgOrderId("order-" + UUID.randomUUID()).amount(amount)
                .orderName("테스트").couponIssuanceIds(List.of()).status("COMPLETED")
                .idempotencyKey(UUID.randomUUID().toString()).createdAt(now).updatedAt(now).build());
    }

    private RefundRequestJpaEntity saveRequest(PaymentJpaEntity payment, String triggerType, String status) {
        return refundRequestJpaRepository.save(RefundRequestJpaEntity.builder()
                .paymentId(payment.getId()).fundingOrderId(UUID.randomUUID()).triggerType(triggerType)
                .status(status).requestedAt(Instant.now()).build());
    }
}
