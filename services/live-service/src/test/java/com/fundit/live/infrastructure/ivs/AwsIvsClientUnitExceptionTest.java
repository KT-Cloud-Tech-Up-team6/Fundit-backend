package com.fundit.live.infrastructure.ivs;

import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ivs.model.ChannelNotBroadcastingException;
import software.amazon.awssdk.services.ivs.model.CreateChannelRequest;
import software.amazon.awssdk.services.ivs.model.GetStreamRequest;
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
        AwsIvsClient client = new AwsIvsClient(ivs, mock(IvschatClient.class), "", "");

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
        AwsIvsClient client = new AwsIvsClient(ivs, mock(IvschatClient.class), "", "");

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
        AwsIvsClient client = new AwsIvsClient(ivs, mock(IvschatClient.class), "", "");

        // when
        int viewerCount = client.getViewerCount("arn:channel");

        // then
        assertThat(viewerCount).isZero();
    }
}
