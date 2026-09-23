package com.fundit.live.infrastructure.ivs;

import com.fundit.live.application.ivs.IvsClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.ivs.model.Channel;
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
import software.amazon.awssdk.services.ivschat.model.SendEventRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "");

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
        new AwsIvsClient(ivs, ivschat, "arn:recording", "").createChannel("s1");
        new AwsIvsClient(ivs, ivschat, "", "").createChannel("s2");

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
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "arn:logging");

        // when
        String roomArn = client.createChatRoom("live-1");
        String token = client.createChatToken(roomArn, "user-1", List.of("SEND_MESSAGE"));

        // then
        ArgumentCaptor<CreateRoomRequest> room = ArgumentCaptor.forClass(CreateRoomRequest.class);
        verify(ivschat).createRoom(room.capture());
        assertThat(room.getValue().loggingConfigurationIdentifiers()).containsExactly("arn:logging");
        assertThat(roomArn).isEqualTo("arn:room");
        assertThat(token).isEqualTo("chat-token");
    }

    @Test
    void 시청자_수를_조회한다() {
        // given
        given(ivs.getStream(any(GetStreamRequest.class))).willReturn(GetStreamResponse.builder()
                .stream(Stream.builder().viewerCount(42L).build())
                .build());
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "");

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
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "");

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
        AwsIvsClient client = new AwsIvsClient(ivs, ivschat, "", "");

        // when
        client.sendChatEvent("arn:room", "seller-answer", Map.of("answer", "500ml입니다"));

        // then
        ArgumentCaptor<SendEventRequest> captor = ArgumentCaptor.forClass(SendEventRequest.class);
        verify(ivschat).sendEvent(captor.capture());
        assertThat(captor.getValue().roomIdentifier()).isEqualTo("arn:room");
        assertThat(captor.getValue().eventName()).isEqualTo("seller-answer");
        assertThat(captor.getValue().attributes()).containsEntry("answer", "500ml입니다");
    }
}
