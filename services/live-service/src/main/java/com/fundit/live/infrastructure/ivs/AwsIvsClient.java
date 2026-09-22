package com.fundit.live.infrastructure.ivs;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ivs.IvsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.ivs.model.ChannelNotBroadcastingException;
import software.amazon.awssdk.services.ivs.model.CreateChannelRequest;
import software.amazon.awssdk.services.ivs.model.CreateChannelResponse;
import software.amazon.awssdk.services.ivs.model.GetStreamRequest;
import software.amazon.awssdk.services.ivschat.IvschatClient;
import software.amazon.awssdk.services.ivschat.model.CreateChatTokenRequest;
import software.amazon.awssdk.services.ivschat.model.CreateRoomRequest;

import java.util.List;
import java.util.function.Supplier;

/**
 * Amazon IVS 실연동. {@code live.ivs.mode=aws}일 때만 뜬다.
 *
 * <p>녹화·채팅 로깅 설정 ARN은 선택이다 — 비어 있으면 요청에서 뺀다. 녹화는 다시보기·하이라이트
 * 입력(S3), 채팅 로깅은 Firehose → 채팅 적재 경로라 인프라가 준비되는 대로 값만 넣으면 된다.
 */
@Component
@ConditionalOnProperty(name = "live.ivs.mode", havingValue = "aws")
public class AwsIvsClient implements IvsClient {

    private final software.amazon.awssdk.services.ivs.IvsClient ivs;
    private final IvschatClient ivschat;
    private final String recordingConfigurationArn;
    private final String chatLoggingConfigurationArn;

    public AwsIvsClient(software.amazon.awssdk.services.ivs.IvsClient ivs,
                        IvschatClient ivschat,
                        @Value("${live.ivs.recording-configuration-arn:}") String recordingConfigurationArn,
                        @Value("${live.ivs.chat-logging-configuration-arn:}") String chatLoggingConfigurationArn) {
        this.ivs = ivs;
        this.ivschat = ivschat;
        this.recordingConfigurationArn = recordingConfigurationArn;
        this.chatLoggingConfigurationArn = chatLoggingConfigurationArn;
    }

    @Override
    public Channel createChannel(String sellerId) {
        CreateChannelRequest.Builder request = CreateChannelRequest.builder().name("seller-" + sellerId);
        if (!recordingConfigurationArn.isBlank()) {
            request.recordingConfigurationArn(recordingConfigurationArn);
        }
        CreateChannelResponse response = call(() -> ivs.createChannel(request.build()));
        // 스트림 키는 값이 아니라 ARN(참조)만 남긴다 — 값은 DB에 두지 않는다(S9).
        return new Channel(
                response.channel().arn(),
                "rtmps://" + response.channel().ingestEndpoint() + ":443/app/",
                response.channel().playbackUrl(),
                response.streamKey().arn());
    }

    @Override
    public String createChatRoom(String liveId) {
        CreateRoomRequest.Builder request = CreateRoomRequest.builder().name("live-" + liveId);
        if (!chatLoggingConfigurationArn.isBlank()) {
            request.loggingConfigurationIdentifiers(chatLoggingConfigurationArn);
        }
        return call(() -> ivschat.createRoom(request.build())).arn();
    }

    @Override
    public String createChatToken(String roomArn, String userId, List<String> capabilities) {
        return call(() -> ivschat.createChatToken(CreateChatTokenRequest.builder()
                .roomIdentifier(roomArn)
                .userId(userId)
                .capabilitiesWithStrings(capabilities)
                .build())).token();
    }

    @Override
    public int getViewerCount(String channelArn) {
        try {
            return call(() -> ivs.getStream(GetStreamRequest.builder().channelArn(channelArn).build()))
                    .stream().viewerCount().intValue();
        } catch (DependencyFailureException e) {
            // ChannelNotBroadcastingException도 SdkException이라 call()이 감싸서 던진다 —
            // 방송이 막 끊긴 세션은 순위에서 0으로 취급하면 충분하다(전체 목록 조회를 막지 않는다).
            if (e.getCause() instanceof ChannelNotBroadcastingException) {
                return 0;
            }
            throw e;
        }
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (SdkException e) {
            throw new DependencyFailureException(e);
        }
    }
}
