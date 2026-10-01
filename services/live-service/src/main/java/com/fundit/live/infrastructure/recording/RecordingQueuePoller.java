package com.fundit.live.infrastructure.recording;

import com.fundit.live.application.session.LiveVodService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * IVS 녹화 완료 이벤트를 SQS에서 꺼내 다시보기 URL로 저장한다(#222). 인프라가 HTTP 대신 큐를 둔 이유는
 * 파드 재배포 중에도 이벤트가 유실되지 않게 하려는 것이다.
 *
 * <p><b>처리하면 지우고, 실패하면 남긴다.</b> 남긴 메시지는 visibility timeout 뒤 다시 들어오고, 계속 실패하면
 * 인프라가 붙인 DLQ로 빠진다. "완료 이벤트가 아님"처럼 저장하지 않는 경우도 정상 처리라 지운다.
 *
 * <p>롱 폴링을 쓰지 않는다 — {@code @Scheduled}는 기본 단일 스레드라 대기하는 동안 채팅 배치 전송(3초 주기)이
 * 밀린다. 녹화 완료는 방송 1회당 한 번 오는 드문 이벤트라 짧은 주기 폴링으로 충분하다.
 *
 * <p>spring-cloud-aws {@code @SqsListener}를 쓰지 않은 이유: 이미 쓰는 AWS SDK에 sqs 모듈만 더하면 되고,
 * 새 프레임워크 의존성의 Boot 4.1 호환을 따로 검증할 필요가 없다.
 */
@Slf4j
@Component
@ConditionalOnExpression("!'${live.recording.queue-url:}'.isBlank()")
public class RecordingQueuePoller {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_MESSAGES = 10;

    private final SqsClient sqs;
    private final LiveVodService liveVodService;
    private final String queueUrl;

    public RecordingQueuePoller(SqsClient sqs, LiveVodService liveVodService,
                                @Value("${live.recording.queue-url}") String queueUrl) {
        this.sqs = sqs;
        this.liveVodService = liveVodService;
        this.queueUrl = queueUrl;
    }

    @Scheduled(fixedDelayString = "${live.recording.poll-interval-ms:5000}")
    public void poll() {
        List<Message> messages;
        try {
            messages = sqs.receiveMessage(r -> r.queueUrl(queueUrl)
                    .maxNumberOfMessages(MAX_MESSAGES)
                    .waitTimeSeconds(0)).messages();
        } catch (RuntimeException e) {
            // 스케줄러 스레드를 죽이지 않는다 — 다음 주기에 다시 받는다
            log.warn("녹화 완료 큐 수신 실패: {}", e.getMessage());
            return;
        }
        messages.forEach(this::handle);
    }

    private void handle(Message message) {
        try {
            JsonNode event = JSON.readTree(message.body());
            JsonNode detail = event.path("detail");
            liveVodService.recordingEnded(
                    textOrNull(event.path("resources").path(0)),
                    textOrNull(detail.path("recording_status")),
                    textOrNull(detail.path("recording_s3_key_prefix")));
            sqs.deleteMessage(r -> r.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
        } catch (RuntimeException e) {
            // 지우지 않는다 — 재수신되고, 계속 실패하면 DLQ로 간다
            log.warn("녹화 완료 메시지 처리 실패(재시도 대기): messageId={}", message.messageId(), e);
        }
    }

    private static String textOrNull(JsonNode node) {
        return node.isString() ? node.asString() : null;
    }
}
