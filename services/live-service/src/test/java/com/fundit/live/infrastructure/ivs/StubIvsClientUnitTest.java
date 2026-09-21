package com.fundit.live.infrastructure.ivs;

import com.fundit.live.application.ivs.IvsClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StubIvsClientUnitTest {

    private final StubIvsClient client = new StubIvsClient("");

    @Test
    void 채널은_판매자별로_다른_값을_돌려준다() {
        // given & when
        IvsClient.Channel a = client.createChannel("seller-a");
        IvsClient.Channel b = client.createChannel("seller-b");

        // then — 전부 같은 값이면 채널이 판매자별로 분리되는지 확인할 수 없다
        assertThat(a.arn()).isNotEqualTo(b.arn());
        assertThat(a.playbackUrl()).isNotEqualTo(b.playbackUrl());
    }

    @Test
    void 스트림_키는_값이_아니라_참조다() {
        // given & when
        IvsClient.Channel channel = client.createChannel("seller-a");

        // then — 탈취되면 무단 송출이 가능한 민감정보라 DB에 값을 두지 않는다(security.md S9)
        assertThat(channel.streamKeyRef()).contains("stream-key-ref");
    }

    @Test
    void 채팅_토큰은_실제_형식을_흉내_내지_않는다() {
        // given & when
        String token = client.createChatToken("room-arn", "user-1", List.of("SEND_MESSAGE"));

        // then — 진짜처럼 보이면 프론트가 IVS에 못 붙는 걸 늦게 발견한다
        assertThat(token).startsWith("stub-chat-token:").contains("user-1").contains("SEND_MESSAGE");
    }

    @Test
    void 테스트_영상_URL을_설정하면_재생_URL로_쓴다() {
        // given — IVS 없이 FE 플레이어를 검증할 S3 테스트 영상
        StubIvsClient configured = new StubIvsClient("https://infrastudy.store/media/test/master.m3u8");

        // when
        IvsClient.Channel channel = configured.createChannel("seller-a");

        // then
        assertThat(channel.playbackUrl()).isEqualTo("https://infrastudy.store/media/test/master.m3u8");
    }
}
