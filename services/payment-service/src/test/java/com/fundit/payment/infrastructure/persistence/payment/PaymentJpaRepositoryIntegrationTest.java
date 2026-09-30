package com.fundit.payment.infrastructure.persistence.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 대기 결제 닫기가 조건부 UPDATE라 이미 승인된 결제를 덮지 않는지 실제 DB로 확인한다. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        "payment.encryption.key=Oz9qy5geAzhDXyHfZdFB3WPwVH8/sx/uD2j5rX5DGkY=",
        "toss.payments.secret-key=test_sk_dummy",
        "media.s3.bucket=unused", "media.s3.region=ap-northeast-2",
        "media.public-base-url=https://cdn.test/media/"
})
@Transactional
class PaymentJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    private PaymentJpaEntity payment(String status) {
        Instant now = Instant.now();
        return paymentJpaRepository.saveAndFlush(PaymentJpaEntity.builder()
                .id(UUID.randomUUID()).memberId(UUID.randomUUID()).pgOrderId("order-" + UUID.randomUUID())
                .amount(10_000L).orderName("테스트").couponIssuanceIds(List.of()).status(status)
                .idempotencyKey(UUID.randomUUID().toString()).createdAt(now).updatedAt(now).build());
    }

    @Test
    void 대기_결제면_FAILED로_바꾼다() {
        // given
        PaymentJpaEntity pending = payment("PENDING");

        // when
        int updated = paymentJpaRepository.failIfPending(pending.getId(), Instant.now());

        // then
        assertThat(updated).isEqualTo(1);
        assertThat(paymentJpaRepository.findById(pending.getId())).get()
                .extracting(PaymentJpaEntity::getStatus).isEqualTo("FAILED");
    }

    @Test
    void 이미_승인된_결제는_바꾸지_않는다() {
        // given — 조회와 닫기 사이에 승인이 먼저 커밋된 경우
        PaymentJpaEntity completed = payment("COMPLETED");

        // when
        int updated = paymentJpaRepository.failIfPending(completed.getId(), Instant.now());

        // then
        assertThat(updated).isZero();
        assertThat(paymentJpaRepository.findById(completed.getId())).get()
                .extracting(PaymentJpaEntity::getStatus).isEqualTo("COMPLETED");
    }
}
