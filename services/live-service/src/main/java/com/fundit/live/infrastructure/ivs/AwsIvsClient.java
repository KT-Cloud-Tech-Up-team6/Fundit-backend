package com.fundit.live.infrastructure.ivs;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ivs.IvsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
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
@Slf4j
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

    /**
     * 실패 종류를 안 가리고 전부 0으로 낮춘다(방송 종료·스로틀링·일시적 네트워크 오류 등) —
     * 세션 하나의 IVS 집계 실패가 전체 실시간 순위 목록 조회를 막으면 안 된다(리뷰 지적으로
     * 발견 — 원래는 {@code ChannelNotBroadcastingException}만 봐주고 나머지는 그대로 던져
     * 이 클래스 javadoc이 말한 의도와 어긋나 있었다). 조용히 삼키지 않고 로그는 남긴다 —
     * 권한·설정 문제가 있으면 로그로라도 드러나야 한다.
     */
    @Override
    public int getViewerCount(String channelArn) {
        try {
            return call(() -> ivs.getStream(GetStreamRequest.builder().channelArn(channelArn).build()))
                    .stream().viewerCount().intValue();
        } catch (DependencyFailureException e) {
            log.warn("IVS 시청자 수 조회 실패, channelArn={}", channelArn, e);
            return 0;
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
