package com.fundit.project.infrastructure.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundingStoryAiClientConfigUnitExceptionTest {

    @Test
    void require_https가_true인데_http_주소면_기동을_막는다() {
        // given & when & then — service-token을 실어 보내는 주소라 평문 전송을 허용하면 안 된다(S9)
        assertThatThrownBy(() -> new FundingStoryAiClientConfig()
                .fundingStoryAiRestClient("http://funding-story-ai:8000", 1000, 2000, true))
                .isInstanceOf(IllegalStateException.class);
    }
}
