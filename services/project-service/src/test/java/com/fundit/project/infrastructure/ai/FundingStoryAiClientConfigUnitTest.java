package com.fundit.project.infrastructure.ai;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class FundingStoryAiClientConfigUnitTest {

    @Test
    void AI_RestClient는_설정된_base_url로_생성된다() {
        RestClient client = new FundingStoryAiClientConfig()
                .fundingStoryAiRestClient("http://funding-story-ai:8000", 1000, 2000, false);

        assertThat(client).isNotNull();
    }

    @Test
    void require_https가_true여도_https_주소면_그대로_생성된다() {
        RestClient client = new FundingStoryAiClientConfig()
                .fundingStoryAiRestClient("https://funding-story-ai:8000", 1000, 2000, true);

        assertThat(client).isNotNull();
    }
}
