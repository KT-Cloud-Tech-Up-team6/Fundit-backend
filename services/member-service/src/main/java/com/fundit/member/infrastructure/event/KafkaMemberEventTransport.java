package com.fundit.member.infrastructure.event;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.common.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link MemberEventTransport}의 Kafka 구현체.
 *
 * <p>payload는 봉투 없이 평평한 JSON이다 — 레코드가 그대로 직렬화되고 {@code eventId}는
 * 그 필드 중 하나다(event-convention.md 4·5번).
 *
 * <p><b>파티션 키는 세 토픽 모두 memberId다.</b> 찜은 한 회원이 같은 프로젝트를 빠르게
 * 찜→해제할 때 순서가 뒤집히면 소비 측에서 지울 행이 없어 카운트가 안 내려간 채 뒤이은
 * wished가 +1 해서 찜하지 않은 회원이 통계에 남는다. 가입도 한 회원 기준이라 같은 키를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class KafkaMemberEventTransport implements MemberEventTransport {

    /**
     * 전송 결과를 기다리는 상한. 아웃박스 워커가 다음 주기에 재시도하므로 길게 잡을 이유가 없고,
     * 한 건이 오래 붙잡으면 같은 배치의 뒤쪽 이벤트가 그만큼 밀린다.
     */
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendWished(WishEvent event) {
        send(KafkaTopics.PROJECT_WISHED, event.memberId().toString(), event);
    }

    @Override
    public void sendUnwished(WishEvent event) {
        send(KafkaTopics.PROJECT_UNWISHED, event.memberId().toString(), event);
    }

    @Override
    public void sendSignedUp(SignedUpEvent event) {
        send(KafkaTopics.MEMBER_SIGNED_UP, event.memberId().toString(), event);
    }

    /**
     * <b>전송 결과를 반드시 기다린다.</b> {@code send()}는 결과를 미래에 채우는 비동기 호출이라
     * 그냥 반환하면 브로커가 죽어 있어도 워커가 성공으로 보고 {@code published_at}을 채운다 —
     * 행이 발행된 척 사라지고 아웃박스를 둔 이유가 통째로 무력화된다.
     *
     * <p>실패는 {@link DependencyFailureException}으로 감싸 워커가 미발행으로 남기게 한다
     * (error-handling.md: 외부 연동 실패는 infrastructure 계층에서 감싼다).
     */
    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // 인터럽트를 삼키면 상위(스케줄러 종료 등)가 중단 신호를 영영 못 본다.
            Thread.currentThread().interrupt();
            throw new DependencyFailureException(e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new DependencyFailureException(e);
        }
    }
}
