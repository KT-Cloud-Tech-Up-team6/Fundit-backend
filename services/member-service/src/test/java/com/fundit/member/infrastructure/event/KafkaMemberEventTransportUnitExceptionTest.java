package com.fundit.member.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.member.infrastructure.event.MemberEventTransport.SignedUpEvent;
import com.fundit.member.infrastructure.event.MemberEventTransport.WishEvent;
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
 * 채워, 브로커가 죽어 있어도 아웃박스 행이 발행된 것처럼 사라진다.
 *
 * <p><b>비동기 실패를 유닛 테스트로 잡는 이유</b>: 브로커 주소가 아예 닿지 않는 경우는
 * {@code send()}가 동기로 던져 {@code MemberEventOutboxIntegrationExceptionTest}가 덮지만,
 * "브로커에는 닿았는데 확인 응답이 실패"하는 경로는 future가 나중에 실패한다 —
 * 실제 브로커로 재현하려면 delivery timeout을 기다려야 해서 여기서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class KafkaMemberEventTransportUnitExceptionTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private KafkaMemberEventTransport transport;

    private void sendFailsAsynchronously() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new KafkaException("브로커 확인 응답 실패")));
    }

    @Test
    void 전송이_비동기로_실패하면_예외가_발생한다() {
        // given
        sendFailsAsynchronously();

        // when & then
        assertThatThrownBy(() -> transport.sendWished(new WishEvent("member:1", UUID.randomUUID(), 1L)))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 가입_이벤트_전송이_실패해도_예외가_발생한다() {
        // given
        sendFailsAsynchronously();

        // when & then
        assertThatThrownBy(() -> transport.sendSignedUp(new SignedUpEvent("member:2", UUID.randomUUID())))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 전송이_동기로_실패해도_예외가_발생한다() {
        // given
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new KafkaException("브로커에 닿지 못함"));

        // when & then
        assertThatThrownBy(() -> transport.sendUnwished(new WishEvent("member:3", UUID.randomUUID(), 1L)))
                .isInstanceOf(DependencyFailureException.class);
    }
}
