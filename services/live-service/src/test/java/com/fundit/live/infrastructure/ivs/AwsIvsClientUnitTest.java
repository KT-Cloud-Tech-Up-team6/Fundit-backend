package com.fundit.live.infrastructure.ivs;

import com.fundit.live.application.ivs.IvsClient;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ivs.model.Channel;
import software.amazon.awssdk.services.ivs.model.ChannelNotBroadcastingException;
import software.amazon.awssdk.services.ivs.model.StopStreamRequest;
import software.amazon.awssdk.services.ivs.model.CreateChannelRequest;
import software.amazon.awssdk.services.ivs.model.CreateChannelResponse;
import software.amazon.awssdk.services.ivs.model.GetStreamKeyRequest;
import software.amazon.awssdk.services.ivs.model.GetStreamKeyResponse;
import software.amazon.awssdk.services.ivs.model.GetStreamRequest;
import software.amazon.awssdk.services.ivs.model.GetStreamResponse;
import software.amazon.awssdk.services.ivs.model.Stream;
import software.amazon.awssdk.services.ivs.model.StreamKey;
import software.amazon.awssdk.services.ivschat.IvschatClient;
import software.amazon.awssdk.services.ivschat.model.CreateChatTokenRequest;
import software.amazon.awssdk.services.ivschat.model.CreateChatTokenResponse;
import software.amazon.awssdk.services.ivschat.model.CreateRoomRequest;
import software.amazon.awssdk.services.ivschat.model.CreateRoomResponse;
import software.amazon.awssdk.services.ivschat.model.FallbackResult;
import software.amazon.awssdk.services.ivschat.model.SendEventRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AwsIvsClientUnitTest {

    private final software.amazon.awssdk.services.ivs.IvsClient ivs =
            mock(software.amazon.awssdk.services.ivs.IvsClient.class);
    private final IvschatClient ivschat = mock(IvschatClient.class);

    private void givenChannelCreated() {
        given(ivs.createChannel(any(CreateChannelRequest.class))).willReturn(CreateChannelResponse.builder()
                .channel(Channel.builder().arn("arn:channel").ingestEndpoint("abc.global-contribute.live-video.net")
                        .playbackUrl("https://play/abc.m3u8").build())
                .streamKey(StreamKey.builder().arn("arn:stream-key").value("sk_secret").build())
                .build());
    }

    @Test
    void 채널을_만들면_스트림_키는_값이_아니라_ARN만_담는다() {
        // given
        givenChannelCreated();
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000);

        // when
        IvsClient.Channel channel = client.createChannel("seller-1");

        // then
        assertThat(channel.arn()).isEqualTo("arn:channel");
        assertThat(channel.ingestEndpoint()).isEqualTo("rtmps://abc.global-contribute.live-video.net:443/app/");
        assertThat(channel.playbackUrl()).isEqualTo("https://play/abc.m3u8");
        assertThat(channel.streamKeyRef()).isEqualTo("arn:stream-key");
    }

    @Test
    void 녹화_설정이_있으면_채널에_연결하고_없으면_뺀다() {
        // given
        givenChannelCreated();
        ArgumentCaptor<CreateChannelRequest> captor = ArgumentCaptor.forClass(CreateChannelRequest.class);

        // when
        new AwsIvsClient(ivs, ivschat, "arn:recording", "", "dev", "ap-northeast-2", 3000).createChannel("s1");
        new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000).createChannel("s2");

        // then
        verify(ivs, org.mockito.Mockito.times(2)).createChannel(captor.capture());
        assertThat(captor.getAllValues().get(0).recordingConfigurationArn()).isEqualTo("arn:recording");
        assertThat(captor.getAllValues().get(1).recordingConfigurationArn()).isNull();
    }

    @Test
    void 채팅방과_채팅_토큰을_발급한다() {
        // given
        given(ivschat.createRoom(any(CreateRoomRequest.class)))
                .willReturn(CreateRoomResponse.builder().arn("arn:room").build());
        given(ivschat.createChatToken(any(CreateChatTokenRequest.class)))
                .willReturn(CreateChatTokenResponse.builder().token("chat-token").build());
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000);

        // when
        String roomArn = client.createChatRoom("live-1");
        String token = client.createChatToken(roomArn, "user-1", List.of("SEND_MESSAGE"), Map.of("nickname", "펀딧러"));

        // then — 닉네임은 서버가 정해 토큰에 싣는다(클라이언트 입력이면 사칭 가능)
        ArgumentCaptor<CreateChatTokenRequest> request = ArgumentCaptor.forClass(CreateChatTokenRequest.class);
        verify(ivschat).createChatToken(request.capture());
        assertThat(request.getValue().attributes()).containsEntry("nickname", "펀딧러");
        assertThat(roomArn).isEqualTo("arn:room");
        assertThat(token).isEqualTo("chat-token");
    }

    @Test
    void 리뷰_핸들러가_있으면_장애_시_차단으로_채팅방에_연결하고_없으면_뺀다() {
        // given
        given(ivschat.createRoom(any(CreateRoomRequest.class)))
                .willReturn(CreateRoomResponse.builder().arn("arn:room").build());
        ArgumentCaptor<CreateRoomRequest> captor = ArgumentCaptor.forClass(CreateRoomRequest.class);

        // when
        new AwsIvsClient(ivs, ivschat, "", "arn:lambda", "dev", "ap-northeast-2", 3000).createChatRoom("live-1");
        new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000).createChatRoom("live-2");

        // then — 필터가 죽었을 때 걸러지지 않은 메시지가 퍼지는 것보다 채팅이 막히는 편이 낫다
        verify(ivschat, org.mockito.Mockito.times(2)).createRoom(captor.capture());
        assertThat(captor.getAllValues().get(0).messageReviewHandler().uri()).isEqualTo("arn:lambda");
        assertThat(captor.getAllValues().get(0).messageReviewHandler().fallbackResult()).isEqualTo(FallbackResult.DENY);
        assertThat(captor.getAllValues().get(1).messageReviewHandler()).isNull();
    }

    @Nested
    class 채팅_프레임_해석 {

        @Test
        void 참가자_메시지는_적재할_값으로_바꾼다() {
            // given — AWS Chat Messaging API "Message (Subscribe)" 형식
            String frame = """
                    {"Type":"MESSAGE","Id":"msg-1","RequestId":"r-1","Content":"사이즈가 어떻게 되나요?",
                     "SendTime":"2026-09-30T11:03:11.123Z","Attributes":{},
                     "Sender":{"UserId":"0199aaaa-0000-7000-8000-000000000001","Attributes":{"nickname":"펀딧러"}}}
                    """;

            // when
            Optional<IvsClient.ChatMessage> message = AwsIvsClient.parseChatFrame(frame);

            // then
            assertThat(message).contains(new IvsClient.ChatMessage("msg-1",
                    "0199aaaa-0000-7000-8000-000000000001", "사이즈가 어떻게 되나요?",
                    Instant.parse("2026-09-30T11:03:11.123Z")));
        }

        @Test
        void 서버발_이벤트는_적재하지_않는다() {
            // given — 우리가 SendEvent로 보낸 ai-answer 등이 되돌아온 것
            String frame = """
                    {"Type":"EVENT","Id":"evt-1","EventName":"ai-answer","SendTime":"2026-09-30T11:03:11Z",
                     "Attributes":{"answer":"500ml입니다"}}
                    """;

            // when & then
            assertThat(AwsIvsClient.parseChatFrame(frame)).isEmpty();
        }

        @Test
        void 오류_프레임은_적재하지_않는다() {
            // given
            String frame = """
                    {"Type":"ERROR","Id":"e-1","ErrorCode":401,"ErrorMessage":"token expired"}
                    """;

            // when & then
            assertThat(AwsIvsClient.parseChatFrame(frame)).isEmpty();
        }
    }

    @Test
    void 시청자_수를_조회한다() {
        // given
        given(ivs.getStream(any(GetStreamRequest.class))).willReturn(GetStreamResponse.builder()
                .stream(Stream.builder().viewerCount(42L).build())
                .build());
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000);

        // when
        int viewerCount = client.getViewerCount("arn:channel");

        // then
        assertThat(viewerCount).isEqualTo(42);
    }

    @Test
    void 스트림_키는_ARN으로_조회해_값을_돌려준다() {
        // given
        given(ivs.getStreamKey(any(GetStreamKeyRequest.class))).willReturn(GetStreamKeyResponse.builder()
                .streamKey(StreamKey.builder().arn("arn:stream-key").value("sk_secret").build())
                .build());
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000);

        // when
        String value = client.getStreamKeyValue("arn:stream-key");

        // then
        ArgumentCaptor<GetStreamKeyRequest> captor = ArgumentCaptor.forClass(GetStreamKeyRequest.class);
        verify(ivs).getStreamKey(captor.capture());
        assertThat(captor.getValue().arn()).isEqualTo("arn:stream-key");
        assertThat(value).isEqualTo("sk_secret");
    }

    @Test
    void 채팅_이벤트는_방_이름_속성을_그대로_보낸다() {
        // given
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000);

        // when
        client.sendChatEvent("arn:room", "seller-answer", Map.of("answer", "500ml입니다"));

        // then
        ArgumentCaptor<SendEventRequest> captor = ArgumentCaptor.forClass(SendEventRequest.class);
        verify(ivschat).sendEvent(captor.capture());
        assertThat(captor.getValue().roomIdentifier()).isEqualTo("arn:room");
        assertThat(captor.getValue().eventName()).isEqualTo("seller-answer");
        assertThat(captor.getValue().attributes()).containsEntry("answer", "500ml입니다");
    }

    @Test
    void 송출_상태는_IVS_스트림_값을_그대로_옮긴다() {
        // given
        java.time.Instant startedAt = java.time.Instant.parse("2026-09-10T11:00:00Z");
        given(ivs.getStream(any(GetStreamRequest.class))).willReturn(GetStreamResponse.builder()
                .stream(Stream.builder().state("LIVE").health("STARVING").viewerCount(42L).startTime(startedAt).build())
                .build());
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000);

        // when
        IvsClient.StreamStatus status = client.getStreamStatus("arn:channel");

        // then
        assertThat(status).isEqualTo(new IvsClient.StreamStatus("LIVE", "STARVING", 42, startedAt));
    }

    @Test
    void 방송이_안_들어오면_송출_상태는_OFFLINE이다() {
        // given — IVS는 송출이 없으면 예외로 알려준다
        given(ivs.getStream(any(GetStreamRequest.class)))
                .willThrow(ChannelNotBroadcastingException.builder().message("not broadcasting").build());
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000);

        // when & then
        assertThat(client.getStreamStatus("arn:channel")).isEqualTo(IvsClient.StreamStatus.OFFLINE);
    }

    @Test
    void 채널_생성_요청에_Environment_태그가_실린다() {
        // given — 인프라 IAM 조건(Infra PR #127)이 이 태그가 있는 생성만 허용한다
        givenChannelCreated();
        ArgumentCaptor<CreateChannelRequest> captor = ArgumentCaptor.forClass(CreateChannelRequest.class);

        // when
        new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000).createChannel("seller-1");

        // then
        verify(ivs).createChannel(captor.capture());
        assertThat(captor.getValue().tags()).isEqualTo(Map.of("Environment", "dev"));
    }

    @Test
    void 채팅방_생성_요청에_Environment_태그가_실린다() {
        // given
        given(ivschat.createRoom(any(CreateRoomRequest.class)))
                .willReturn(CreateRoomResponse.builder().arn("arn:room").build());
        ArgumentCaptor<CreateRoomRequest> captor = ArgumentCaptor.forClass(CreateRoomRequest.class);

        // when
        new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000).createChatRoom("live-1");

        // then
        verify(ivschat).createRoom(captor.capture());
        assertThat(captor.getValue().tags()).isEqualTo(Map.of("Environment", "dev"));
    }

    @Test
    void 송출_중지는_채널_ARN으로_요청한다() {
        // given
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000);

        // when
        client.stopStream("arn:channel");

        // then
        ArgumentCaptor<StopStreamRequest> captor = ArgumentCaptor.forClass(StopStreamRequest.class);
        verify(ivs).stopStream(captor.capture());
        assertThat(captor.getValue().channelArn()).isEqualTo("arn:channel");
    }

    @Test
    void 이미_송출이_없으면_송출_중지는_성공으로_본다() {
        // given
        given(ivs.stopStream(any(StopStreamRequest.class)))
                .willThrow(ChannelNotBroadcastingException.builder().message("not broadcasting").build());
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000);

        // when & then
        assertThatCode(() -> client.stopStream("arn:channel")).doesNotThrowAnyException();
    }

    @Test
    void 목업_채널은_IVS를_부르지_않고_시청자_0명_OFFLINE으로_답한다() {
        // given (#232) — 시드의 가짜 ARN은 IVS가 권한 오류로 거절한다
        String stubArn = "arn:aws:ivs:ap-northeast-2:000000000000:channel/stub-001";
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "", "dev", "ap-northeast-2", 3000);

        // when
        int viewerCount = client.getViewerCount(stubArn);
        IvsClient.StreamStatus status = client.getStreamStatus(stubArn);
        client.stopStream(stubArn);

        // then
        assertThat(viewerCount).isZero();
        assertThat(status).isEqualTo(IvsClient.StreamStatus.OFFLINE);
        verifyNoInteractions(ivs);
    }
}
