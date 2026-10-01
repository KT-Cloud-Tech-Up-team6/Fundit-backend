package com.fundit.live.infrastructure.recording;

import com.fundit.live.application.session.LiveVodService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    void 처리_중_예외가_나면_지우지_않아_재수신되게_한다() throws IOException {
        // given — DB 오류 등. 지우면 그 녹화의 다시보기가 영영 안 생긴다
        givenMessages(message("1", sample()));
        willThrow(new IllegalStateException("db down"))
                .given(liveVodService).recordingEnded(anyString(), anyString(), anyString());

        // when
        poller.poll();

        // then
        verify(sqs, never()).deleteMessage(any(Consumer.class));
    }

    @Test
    void 깨진_메시지는_남기고_다음_메시지는_계속_처리한다() throws IOException {
        // given
        givenMessages(message("bad", "{not json"), message("2", sample()));
        captureDeletes();

        // when
        poller.poll();

        // then
        assertThat(deletedReceipts).containsExactly("rh-2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 수신_자체가_실패해도_예외를_밖으로_던지지_않는다() {
        // given — 스케줄러 스레드가 죽으면 이후 녹화 완료를 전부 놓친다
        given(sqs.receiveMessage(any(Consumer.class))).willThrow(SdkClientException.create("network"));

        // when & then
        assertThatCode(() -> poller.poll()).doesNotThrowAnyException();
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
