package com.fundit.live.infrastructure.ivs;

import com.fundit.live.application.ivs.IvsClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IVS 자격증명이 내려오기 전까지 쓰는 스텁. {@code live.ivs.mode=stub}(기본값)일 때 뜬다.
 *
 * <p>실제 AWS를 부르지 않으므로 통합테스트도 이걸로 돈다 — 테스트가 외부 계정·비용·가용성에
 * 묶이지 않는다. 자격증명이 준비되면 {@code AwsIvsClient}를 추가하고 프로퍼티만 바꾼다.
 */
@Component
@ConditionalOnProperty(name = "live.ivs.mode", havingValue = "stub", matchIfMissing = false)
public class StubIvsClient implements IvsClient {

    private static final String ARN_PREFIX = "arn:aws:ivs:ap-northeast-2:000000000000:channel/stub-";

    @Override
    public Channel createChannel(String sellerId) {
        return new Channel(
                ARN_PREFIX + sellerId,
                "rtmps://stub.ingest.live.local:443/app/",
                "https://stub.playback.live.local/" + sellerId + "/master.m3u8",
                // 스트림 키 "값"이 아니라 참조 식별자다. 실제 구현에서도 값을 DB에 넣지 않는다(S9).
                "stub-stream-key-ref/" + sellerId);
    }

    @Override
    public String createChatRoom(String liveId) {
        return "arn:aws:ivschat:ap-northeast-2:000000000000:room/stub-" + liveId;
    }

    @Override
    public String createChatToken(String roomArn, String userId, List<String> capabilities) {
        // 실제 토큰 형식을 흉내 내지 않는다 — 흉내 내면 스텁 토큰이 진짜처럼 보여
        // 프론트가 IVS에 붙는 실패를 늦게 발견한다.
        return "stub-chat-token:" + userId + ":" + String.join(",", capabilities);
    }
}
