package com.fundit.order.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.notification.OrderNotificationPublisher.CouponExpiringEvent;
import com.fundit.order.application.notification.OrderNotificationPublisher.RewardRestockedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 전송 실패가 호출자에게 전달되는지 본다. 이걸 놓치면 워커가 성공으로 보고 published_at을
 * 채워, 브로커가 죽어 있어도 아웃박스 행이 발행된 것처럼 사라진다(member-service
 * {@code KafkaMemberEventTransportUnitExceptionTest}와 동일 패턴).
 */
@ExtendWith(MockitoExtension.class)
class KafkaOrderNotificationTransportUnitExceptionTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaOrderNotificationTransport transport;

    private void sendFailsAsynchronously() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("브로커 확인 응답 실패")));
    }

    @Test
    void 재입고_알림_전송이_비동기로_실패하면_예외가_발생한다() {
        // given
        sendFailsAsynchronously();

        // when & then
        assertThatThrownBy(() -> transport.sendRewardRestocked(
                new RewardRestockedEvent(5L, UUID.randomUUID()), 1L))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 쿠폰만료임박_알림_전송이_동기로_실패해도_예외가_발생한다() {
        // given
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new KafkaException("브로커에 닿지 못함"));

        // when & then
        assertThatThrownBy(() -> transport.sendCouponExpiring(
                new CouponExpiringEvent(7L, UUID.randomUUID()), 2L))
                .isInstanceOf(DependencyFailureException.class);
    }
}
