package com.fundit.payment.infrastructure.persistence.payment;

import com.fundit.payment.domain.payment.Payment;
import com.fundit.payment.domain.payment.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentPersistenceAdapterUnitTest {

    @Mock
    private PaymentJpaRepository jpaRepository;

    private PaymentPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PaymentPersistenceAdapter(jpaRepository, new PaymentMapper());
    }

    @Test
    void 저장하면_매퍼를_거쳐_도메인을_반환한다() {
        Payment payment = Payment.create(1024L, UUID.randomUUID(), "fundit-1", 10_000L, "주문", null, "idem");
        when(jpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment saved = adapter.save(payment);

        assertThat(saved.getPgOrderId()).isEqualTo("fundit-1");
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void 조회_메서드는_Optional을_그대로_전달한다() {
        Payment payment = Payment.create(1024L, UUID.randomUUID(), "fundit-1", 10_000L, "주문", null, "idem");
        PaymentJpaEntity entity = new PaymentMapper().toEntity(payment);
        when(jpaRepository.findById(payment.getId())).thenReturn(Optional.of(entity));
        when(jpaRepository.findByPgOrderId("fundit-1")).thenReturn(Optional.of(entity));
        when(jpaRepository.findByPgPaymentKey("key")).thenReturn(Optional.empty());
        when(jpaRepository.findByIdempotencyKey("idem")).thenReturn(Optional.of(entity));
        when(jpaRepository.findByCompletedFundingId(1024L)).thenReturn(Optional.empty());
        when(jpaRepository.findFirstByFundingIdAndStatusInOrderByCreatedAtDesc(1024L,
                List.of(PaymentStatus.COMPLETED.name(), PaymentStatus.CANCELLED.name())))
                .thenReturn(Optional.of(entity));
        when(jpaRepository.findFirstByFundingIdAndStatusOrderByCreatedAtDesc(1024L, PaymentStatus.PENDING.name()))
                .thenReturn(Optional.of(entity));
        when(jpaRepository.existsByPgOrderId("fundit-1")).thenReturn(true);

        assertThat(adapter.findById(payment.getId())).isPresent();
        assertThat(adapter.findByPgOrderId("fundit-1")).isPresent();
        assertThat(adapter.findByPgPaymentKey("key")).isEmpty();
        assertThat(adapter.findByIdempotencyKey("idem")).isPresent();
        assertThat(adapter.findCompletedByFundingId(1024L)).isEmpty();
        assertThat(adapter.findCompletedOrCancelledByFundingId(1024L)).isPresent();
        assertThat(adapter.findPendingByFundingId(1024L)).isPresent();
        assertThat(adapter.existsByPgOrderId("fundit-1")).isTrue();
        verify(jpaRepository).existsByPgOrderId("fundit-1");
    }
}
