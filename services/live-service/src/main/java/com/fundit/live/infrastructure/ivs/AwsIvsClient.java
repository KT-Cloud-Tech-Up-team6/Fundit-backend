package com.fundit.live.infrastructure.ivs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ivs.IvsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.ivs.model.ChannelNotBroadcastingException;
import software.amazon.awssdk.services.ivs.model.CreateChannelRequest;
import software.amazon.awssdk.services.ivs.model.CreateChannelResponse;
import software.amazon.awssdk.services.ivs.model.GetStreamKeyRequest;
import software.amazon.awssdk.services.ivs.model.GetStreamRequest;
import software.amazon.awssdk.services.ivs.model.Stream;
import software.amazon.awssdk.services.ivschat.IvschatClient;
import software.amazon.awssdk.services.ivschat.model.CreateChatTokenRequest;
import software.amazon.awssdk.services.ivschat.model.CreateRoomRequest;
import software.amazon.awssdk.services.ivschat.model.FallbackResult;
import software.amazon.awssdk.services.ivschat.model.MessageReviewHandler;
import software.amazon.awssdk.services.ivschat.model.SendEventRequest;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Amazon IVS 실연동. {@code live.ivs.mode=aws}일 때만 뜬다.
 *
 * <p>녹화 설정 ARN·채팅 리뷰 핸들러 ARN은 선택이다 — 비어 있으면 요청에서 뺀다. 녹화는 다시보기·하이라이트
 * 입력(S3), 리뷰 핸들러는 부적절 메시지 차단(Lambda)이라 인프라가 준비되는 대로 값만 넣으면 된다.
 *
 * <p>채팅 적재는 IVS Chat 로깅(Firehose·S3)이 아니라 {@link #openChatConnection} 구독으로 한다 —
 * Firehose는 쓰지 못할 수 있고 S3는 약 5분 지연이라 AI 3분 집계에 못 맞춘다. 구독은 인프라 구성이
 * 필요 없고 지연도 거의 없다.
 *
 * <p>채널·채팅방 생성 요청에는 {@code Environment} 태그를 붙인다 — 인프라 IAM 정책(Infra PR #127)이
 * 이 태그가 있는 생성만 허용한다. 값이 없으면 기동을 실패시킨다: 태그 없이 뜨면 첫 LIVE 생성에서야
 * AccessDenied로 드러난다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "live.ivs.mode", havingValue = "aws")
public class AwsIvsClient implements IvsClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final software.amazon.awssdk.services.ivs.IvsClient ivs;
    private final IvschatClient ivschat;
    private final String recordingConfigurationArn;
    private final String chatReviewHandlerArn;
    private final Map<String, String> tags;
    private final URI chatEndpoint;
    private final Duration connectTimeout;
    private final HttpClient httpClient;

    public AwsIvsClient(software.amazon.awssdk.services.ivs.IvsClient ivs,
                        IvschatClient ivschat,
                        @Value("${live.ivs.recording-configuration-arn:}") String recordingConfigurationArn,
                        @Value("${live.ivs.chat-review-handler-arn:}") String chatReviewHandlerArn,
                        @Value("${live.ivs.environment:}") String environment,
                        @Value("${live.ivs.region}") String region,
                        @Value("${live.ivs.api-timeout-ms}") long timeoutMs) {
        if (environment == null || environment.isBlank()) {
            throw new IllegalStateException("live.ivs.environment(LIVE_IVS_ENVIRONMENT)가 비어 있다 — "
                    + "IVS 생성 요청의 Environment 태그 값이다. 없으면 IAM 조건에 걸려 채널·채팅방을 만들 수 없다.");
        }
        this.ivs = ivs;
        this.ivschat = ivschat;
        this.recordingConfigurationArn = recordingConfigurationArn;
        this.chatReviewHandlerArn = chatReviewHandlerArn;
        this.tags = Map.of("Environment", environment);
        // 채팅 메시징 엔드포인트는 채팅방과 같은 리전이어야 한다(AWS Chat Messaging API 문서).
        this.chatEndpoint = URI.create("wss://edge.ivschat." + region + ".amazonaws.com");
        this.connectTimeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    @Override
    public Channel createChannel(String sellerId) {
        CreateChannelRequest.Builder request = CreateChannelRequest.builder().name("seller-" + sellerId).tags(tags);
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
        CreateRoomRequest.Builder request = CreateRoomRequest.builder().name("live-" + liveId).tags(tags);
        if (!chatReviewHandlerArn.isBlank()) {
            // DENY: 필터(Lambda)가 죽었을 때 걸러지지 않은 메시지가 전 시청자에게 퍼지는 것보다
            // 그동안 채팅이 막히는 편이 낫다(security.md S2, LiveDomainApiSpec.md 채팅 필터링 절).
            request.messageReviewHandler(MessageReviewHandler.builder()
                    .uri(chatReviewHandlerArn)
                    .fallbackResult(FallbackResult.DENY)
                    .build());
        }
        return call(() -> ivschat.createRoom(request.build())).arn();
    }

    @Override
    public String createChatToken(String roomArn, String userId, List<String> capabilities,
                                  Map<String, String> attributes) {
        return call(() -> ivschat.createChatToken(CreateChatTokenRequest.builder()
                .roomIdentifier(roomArn)
                .userId(userId)
                .capabilitiesWithStrings(capabilities)
                .attributes(attributes)
                .build())).token();
    }

    /** 토큰은 핸드셰이크의 {@code Sec-WebSocket-Protocol} 헤더로 보낸다(AWS Chat Messaging API 문서 "Connecting to a Room"). */
    @Override
    public ChatConnection openChatConnection(String token, Consumer<ChatMessage> onMessage) {
        ChatListener listener = new ChatListener(onMessage);
        WebSocket webSocket;
        try {
            webSocket = httpClient.newWebSocketBuilder()
                    .subprotocols(token)
                    .connectTimeout(connectTimeout)
                    .buildAsync(chatEndpoint, listener)
                    .get(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DependencyFailureException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new DependencyFailureException(e);
        }
        return new ChatConnection() {
            @Override
            public boolean isOpen() {
                return listener.open && !webSocket.isInputClosed();
            }

            @Override
            public void close() {
                listener.open = false;
                webSocket.abort();
            }
        };
    }

    /**
     * WebSocket 텍스트 프레임 하나를 해석한다. 참가자 메시지({@code Type=MESSAGE})만 돌려주고
     * 서버발 이벤트({@code EVENT} — 우리가 보낸 {@code ai-answer} 등)·오류({@code ERROR})는 비운다.
     * 형식은 AWS Chat Messaging API 문서 "Message (Subscribe)".
     */
    static Optional<ChatMessage> parseChatFrame(String frame) {
        ChatFrame parsed = JSON.readValue(frame, ChatFrame.class);
        if ("ERROR".equals(parsed.type())) {
            log.warn("IVS 채팅 구독 오류 프레임: {}", frame);
        }
        if (!"MESSAGE".equals(parsed.type()) || parsed.id() == null || parsed.sender() == null
                || parsed.sender().userId() == null || parsed.content() == null || parsed.sendTime() == null) {
            return Optional.empty();
        }
        return Optional.of(new ChatMessage(parsed.id(), parsed.sender().userId(), parsed.content(),
                Instant.parse(parsed.sendTime())));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatFrame(@JsonProperty("Type") String type,
                     @JsonProperty("Id") String id,
                     @JsonProperty("Content") String content,
                     @JsonProperty("SendTime") String sendTime,
                     @JsonProperty("Sender") Sender sender) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Sender(@JsonProperty("UserId") String userId) {
        }
    }

    /** 분할된 텍스트 프레임은 {@code last}까지 모아서 해석한다. 한 프레임이 깨져도 연결은 유지한다. */
    private static final class ChatListener implements WebSocket.Listener {

        private final Consumer<ChatMessage> onMessage;
        private final StringBuilder buffer = new StringBuilder();
        private volatile boolean open = true;

        private ChatListener(Consumer<ChatMessage> onMessage) {
            this.onMessage = onMessage;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String frame = buffer.toString();
                buffer.setLength(0);
                try {
                    parseChatFrame(frame).ifPresent(onMessage);
                } catch (RuntimeException e) {
                    log.warn("IVS 채팅 프레임 처리 실패", e);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            open = false;
            log.info("IVS 채팅 구독 종료, status={} reason={}", statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            open = false;
            log.warn("IVS 채팅 구독 오류", error);
        }
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

    /** 방송이 안 들어오는 것은 IVS가 예외로 알려준다 — 그것만 OFFLINE으로 바꾸고 나머지는 던진다. */
    @Override
    public StreamStatus getStreamStatus(String channelArn) {
        return call(() -> {
            try {
                Stream stream = ivs.getStream(GetStreamRequest.builder().channelArn(channelArn).build()).stream();
                return new StreamStatus(stream.stateAsString(), stream.healthAsString(),
                        stream.viewerCount() == null ? 0 : stream.viewerCount().intValue(), stream.startTime());
            } catch (ChannelNotBroadcastingException e) {
                return StreamStatus.OFFLINE;
            }
        });
    }

    @Override
    public String getStreamKeyValue(String streamKeyRef) {
        return call(() -> ivs.getStreamKey(GetStreamKeyRequest.builder().arn(streamKeyRef).build()))
                .streamKey().value();
    }

    @Override
    public void sendChatEvent(String roomArn, String eventName, Map<String, String> attributes) {
        call(() -> ivschat.sendEvent(SendEventRequest.builder()
                .roomIdentifier(roomArn).eventName(eventName).attributes(attributes).build()));
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (SdkException e) {
            throw new DependencyFailureException(e);
        }
    }
}
