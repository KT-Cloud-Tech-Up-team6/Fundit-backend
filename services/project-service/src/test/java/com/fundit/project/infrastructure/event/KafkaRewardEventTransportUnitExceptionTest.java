package com.fundit.project.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.project.application.reward.RewardEventPublisher.RewardCreatedEvent;
import com.fundit.project.application.reward.RewardEventPublisher.RewardUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

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
class KafkaRewardEventTransportUnitExceptionTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaRewardEventTransport transport;

    private void sendFailsAsynchronously() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("브로커 확인 응답 실패")));
    }

    @Test
    void 생성_이벤트_전송이_비동기로_실패하면_예외가_발생한다() {
        // given
        sendFailsAsynchronously();

        // when & then
        assertThatThrownBy(() -> transport.sendCreated(new RewardCreatedEvent(1L, 1L, false, null), 1L))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 수정_이벤트_전송이_동기로_실패해도_예외가_발생한다() {
        // given
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new KafkaException("브로커에 닿지 못함"));

        // when & then
        assertThatThrownBy(() -> transport.sendUpdated(new RewardUpdatedEvent(1L, 1L, true, 10), 2L))
                .isInstanceOf(DependencyFailureException.class);
    }
}
