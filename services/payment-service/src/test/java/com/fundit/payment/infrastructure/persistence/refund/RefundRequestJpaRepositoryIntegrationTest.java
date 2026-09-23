package com.fundit.payment.infrastructure.persistence.refund;

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
}
