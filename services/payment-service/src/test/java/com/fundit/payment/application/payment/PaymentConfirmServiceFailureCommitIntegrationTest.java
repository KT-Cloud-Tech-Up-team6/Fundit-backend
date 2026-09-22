package com.fundit.payment.application.payment;

import com.fundit.common.error.BusinessException;
import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.payment.PaymentStatus;
import com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 회귀 테스트 — 토스 승인 실패로 confirm()의 @Transactional 전체가 롤백되어도 FAILED 기록은
 * {@link PaymentFailureRecorder}의 별도 트랜잭션(REQUIRES_NEW)으로 실제 커밋되는지 검증한다.
 *
 * <p>{@link PaymentConfirmServiceIntegrationTest}와 달리 클래스에 {@code @Transactional}을 붙이지
 * 않는다 — 테스트 트랜잭션과 서비스 트랜잭션이 하나로 묶이면 confirm()이 실제로 커밋했는지와
 * 무관하게 같은(아직 롤백 안 된) 트랜잭션 안에서 읽기만 해도 값이 보여서 버그를 못 잡는다
 * (수정 전 실제로 그랬다 — DB엔 PENDING만 남는데 테스트는 통과했다).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        "payment.encryption.key=Oz9qy5geAzhDXyHfZdFB3WPwVH8/sx/uD2j5rX5DGkY=",
        "toss.payments.secret-key=test_sk_dummy",
        "media.s3.bucket=unused", "media.s3.region=ap-northeast-2"
})
class PaymentConfirmServiceFailureCommitIntegrationTest {

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
    void 승인이_실패해도_FAILED_기록은_실제로_커밋되어_재시도시_새_pgOrderId가_발급된다() {
        // given
        Payment payment = Payment.create(new UUID(0L, 9001L), UUID.randomUUID(), "fundit-order-commit-1",
                77_000L, "테스트 주문", null, "idem-commit-1");
        Payment saved = paymentRepository.save(payment);
        when(tossPaymentsClient.confirm(anyString(), anyString(), anyLong()))
                .thenThrow(new TossApiException("REJECT_CARD_COMPANY", "한도초과"));

        // when — confirm() 전체 트랜잭션은 예외로 롤백된다
        assertThrows(BusinessException.class, () -> paymentConfirmService.confirm(saved.getMemberId(),
                "pay_key_commit_1", "fundit-order-commit-1", 77_000L));

        // then — 완전히 별도로 다시 조회해도(테스트 트랜잭션 공유 없음) FAILED가 실제로 남아 있다
        Payment reloaded = paymentRepository.findByPgOrderId("fundit-order-commit-1").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.FAILED);

        // then — 이제 이 pgOrderId는 PENDING이 아니므로 PaymentCreateService가 재사용하지 않는다
        assertThat(paymentRepository.findPendingByFundingId(saved.getFundingId())).isEmpty();

        // then — 승인 실패는 아웃박스에 아무것도 남기지 않는다
        var pending = outboxRepository.findByPublishedAtIsNullOrderByIdAsc(PageRequest.of(0, 10));
        assertThat(pending).extracting(com.fundit.payment.infrastructure.persistence.event.PaymentEventOutboxJpaEntity::getFundingId)
                .doesNotContain(saved.getFundingId());
    }
}
