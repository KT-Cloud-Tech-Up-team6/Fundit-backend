package com.fundit.live.infrastructure.recording;

import com.fundit.live.application.session.LiveVodService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 실패 경우는 {@link RecordingQueuePollerUnitExceptionTest} 참고. */
@ExtendWith(MockitoExtension.class)
class RecordingQueuePollerUnitTest {

    private static final String QUEUE_URL = "https://sqs.ap-northeast-2.amazonaws.com/899957568205/queue";

    @Mock private SqsClient sqs;
    @Mock private LiveVodService liveVodService;

    private RecordingQueuePoller poller;
    private final List<String> deletedReceipts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        poller = new RecordingQueuePoller(sqs, liveVodService, QUEUE_URL);
    }

    /** 인프라가 준 실제 이벤트 원문(vod.md 2절, 10-01). 필드 위치가 바뀌면 이 테스트가 깨져야 한다. */
    private static String sample() throws IOException {
        try (InputStream in = RecordingQueuePollerUnitTest.class.getResourceAsStream("/ivs/recording-ended.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private void givenMessages(Message... messages) {
        given(sqs.receiveMessage(any(Consumer.class)))
                .willReturn(ReceiveMessageResponse.builder().messages(messages).build());
    }

    @SuppressWarnings("unchecked")
    private void captureDeletes() {
        given(sqs.deleteMessage(any(Consumer.class))).willAnswer(inv -> {
            DeleteMessageRequest.Builder b = DeleteMessageRequest.builder();
            ((Consumer<DeleteMessageRequest.Builder>) inv.getArgument(0)).accept(b);
            deletedReceipts.add(b.build().receiptHandle());
            return null;
        });
    }

    private static Message message(String id, String body) {
        return Message.builder().messageId(id).receiptHandle("rh-" + id).body(body).build();
    }

    @Test
    void 실제_이벤트_샘플을_읽어_채널_상태_경로를_넘기고_메시지를_지운다() throws IOException {
        // given
        givenMessages(message("1", sample()));
        captureDeletes();

        // when
        poller.poll();

        // then
        verify(liveVodService).recordingEnded(
                "arn:aws:ivs:ap-northeast-2:899957568205:channel/fundit-dev-channel-1",
                "Recording End",
                "ivs/v1/899957568205/fundit-dev-channel-1/2026/10/1/6/0/st-1234567890abcdef");
        assertThat(deletedReceipts).containsExactly("rh-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 수신_요청은_큐_URL로_짧은_폴링을_한다() {
        // given
        given(sqs.receiveMessage(any(Consumer.class))).willAnswer(inv -> {
            ReceiveMessageRequest.Builder b = ReceiveMessageRequest.builder();
            ((Consumer<ReceiveMessageRequest.Builder>) inv.getArgument(0)).accept(b);
            ReceiveMessageRequest request = b.build();
            assertThat(request.queueUrl()).isEqualTo(QUEUE_URL);
            assertThat(request.waitTimeSeconds()).isZero();
            return ReceiveMessageResponse.builder().build();
        });

        // when
        poller.poll();

        // then
        verify(liveVodService, never()).recordingEnded(any(), any(), any());
    }
}
