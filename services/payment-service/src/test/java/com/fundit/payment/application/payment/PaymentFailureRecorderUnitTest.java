package com.fundit.payment.application.payment;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentRepository;
import com.fundit.payment.domain.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFailureRecorderUnitTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentFailureRecorder paymentFailureRecorder;

    @BeforeEach
    void setUp() {
        paymentFailureRecorder = new PaymentFailureRecorder(paymentRepository);
    }

    @Test
    void 실패를_기록하면_상태가_FAILED로_저장된다() {
        // given
        Payment payment = Payment.create(new UUID(0L, 1024L), UUID.randomUUID(), "fundit-order-1", 89_000L, "테스트 주문",
                null, "idem");
        when(paymentRepository.findById(payment.getId())).thenReturn(java.util.Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        // when
        paymentFailureRecorder.recordFailure(payment);

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
    }

    @Test
    void 이미_COMPLETED로_커밋된_결제는_FAILED로_덮어쓰지_않는다() {
        // given — confirm()이 들고 있던 오래된 PENDING 스냅샷 대신, 그사이 다른 트랜잭션이 커밋한 최신 상태를 봐야 한다
        Payment stalePending = Payment.create(new UUID(0L, 1024L), UUID.randomUUID(), "fundit-order-1", 89_000L, "테스트 주문",
                null, "idem");
        Payment nowCompleted = stalePending.toBuilder().status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findById(stalePending.getId())).thenReturn(java.util.Optional.of(nowCompleted));

        // when
        paymentFailureRecorder.recordFailure(stalePending);

        // then
        verify(paymentRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
