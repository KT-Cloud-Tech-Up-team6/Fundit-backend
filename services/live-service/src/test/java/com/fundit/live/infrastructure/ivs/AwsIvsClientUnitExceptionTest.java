package com.fundit.live.infrastructure.ivs;

import com.fundit.common.error.DependencyFailureException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ivs.model.CreateChannelRequest;
import software.amazon.awssdk.services.ivschat.IvschatClient;

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
}
