package com.fundit.live.infrastructure.ivs;

import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ivs.model.ChannelNotBroadcastingException;
import software.amazon.awssdk.services.ivs.model.CreateChannelRequest;
import software.amazon.awssdk.services.ivs.model.GetStreamRequest;
import software.amazon.awssdk.services.ivs.model.StopStreamRequest;
import software.amazon.awssdk.services.ivschat.IvschatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AwsIvsClientUnitExceptionTest {

    @Test
    void AWS_호출이_실패하면_DependencyFailureException으로_감싼다() {
        // given — 권한 부족·타임아웃 등. 호출부(LiveStreamService)가 이 예외로 ERROR 상태를 남긴다
        software.amazon.awssdk.services.ivs.IvsClient ivs = mock(software.amazon.awssdk.services.ivs.IvsClient.class);
        given(ivs.createChannel(any(CreateChannelRequest.class)))
                .willThrow(SdkClientException.create("timeout"));
        AwsIvsClient client = new AwsIvsClient(ivs, mock(IvschatClient.class), "", "", "dev", "ap-northeast-2", 3000);

        // when & then
        assertThatThrownBy(() -> client.createChannel("seller-1"))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 방송_중이_아니면_시청자수는_0이다() {
        // given — 순위 목록 조회 자체를 막으면 안 된다(전체 요청 실패보다 0이 낫다)
        software.amazon.awssdk.services.ivs.IvsClient ivs = mock(software.amazon.awssdk.services.ivs.IvsClient.class);
        given(ivs.getStream(any(GetStreamRequest.class)))
                .willThrow(ChannelNotBroadcastingException.builder().message("not broadcasting").build());
        AwsIvsClient client = new AwsIvsClient(ivs, mock(IvschatClient.class), "", "", "dev", "ap-northeast-2", 3000);

        // when
        int viewerCount = client.getViewerCount("arn:channel");

        // then
        assertThat(viewerCount).isZero();
    }

    @Test
    void 기타_IVS_호출_실패도_시청자수는_0이다() {
        // given — ChannelNotBroadcasting뿐 아니라 스로틀링·타임아웃 등 어떤 실패든
        // 순위 목록 조회를 막으면 안 된다(리뷰 지적으로 원래 좁았던 처리 범위를 넓힘)
        software.amazon.awssdk.services.ivs.IvsClient ivs = mock(software.amazon.awssdk.services.ivs.IvsClient.class);
        given(ivs.getStream(any(GetStreamRequest.class)))
                .willThrow(SdkClientException.create("throttled"));
        AwsIvsClient client = new AwsIvsClient(ivs, mock(IvschatClient.class), "", "", "dev", "ap-northeast-2", 3000);

        // when
        int viewerCount = client.getViewerCount("arn:channel");

        // then
        assertThat(viewerCount).isZero();
    }

    @Test
    void 송출_상태_조회는_방송_중_아님_외의_실패를_OFFLINE으로_감추지_않는다() {
        // given — "모름"을 OFFLINE으로 보여주면 판매자가 멀쩡한 송출을 끊고 다시 켠다
        software.amazon.awssdk.services.ivs.IvsClient ivs = mock(software.amazon.awssdk.services.ivs.IvsClient.class);
        given(ivs.getStream(any(GetStreamRequest.class)))
                .willThrow(SdkClientException.create("throttled"));
        AwsIvsClient client = new AwsIvsClient(ivs, mock(IvschatClient.class), "", "", "dev", "ap-northeast-2", 3000);

        // when & then
        assertThatThrownBy(() -> client.getStreamStatus("arn:channel"))
                .isInstanceOf(DependencyFailureException.class);
    }

    @Test
    void 깨진_채팅_프레임은_예외로_알린다() {
        // given — 구독 리스너가 잡아 로그만 남기고 연결은 유지한다
        String frame = "{not-json";

        // when & then
        assertThatThrownBy(() -> AwsIvsClient.parseChatFrame(frame))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void 환경_태그_값이_비어_있으면_생성할_수_없다() {
        // given — 태그 없이 뜨면 첫 LIVE 생성에서야 AccessDenied로 드러난다. 기동에서 막는다
        software.amazon.awssdk.services.ivs.IvsClient ivs = mock(software.amazon.awssdk.services.ivs.IvsClient.class);

        // when & then
        assertThatThrownBy(() -> new AwsIvsClient(ivs, mock(IvschatClient.class), "", "", " ", "ap-northeast-2", 3000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVE_IVS_ENVIRONMENT");
    }

    @Test
    void 송출_중지는_방송_중_아님_외의_실패를_던진다() {
        // given — 권한 부족 등은 호출부가 로그로 드러내야 한다(인프라 권한 누락 확인용)
        software.amazon.awssdk.services.ivs.IvsClient ivs = mock(software.amazon.awssdk.services.ivs.IvsClient.class);
        given(ivs.stopStream(any(StopStreamRequest.class)))
                .willThrow(SdkClientException.create("AccessDenied"));
        AwsIvsClient client = new AwsIvsClient(ivs, mock(IvschatClient.class), "", "", "dev", "ap-northeast-2", 3000);

        // when & then
        assertThatThrownBy(() -> client.stopStream("arn:channel"))
                .isInstanceOf(DependencyFailureException.class);
    }
}
