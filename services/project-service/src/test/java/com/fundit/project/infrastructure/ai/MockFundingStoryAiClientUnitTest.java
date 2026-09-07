package com.fundit.project.infrastructure.ai;

import com.fundit.project.domain.aifundingstory.FundingStoryResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockFundingStoryAiClientUnitTest {

    private final MockFundingStoryAiClient client = new MockFundingStoryAiClient();

    @Test
    void 제품설명을_INTRO_섹션_본문으로_사용한다() {
        // when
        FundingStoryResult result = client.generate("캠핑용 프라이팬입니다.", List.of("http://img1.jpg"), List.of());

        // then
        assertThat(result.sections()).hasSize(1);
        assertThat(result.sections().get(0).body()).isEqualTo("캠핑용 프라이팬입니다.");
        assertThat(result.imagesSource()).hasSize(1);
        assertThat(result.imagesSource().get(0).source()).isEqualTo("UPLOADED");
        assertThat(result.warnings()).isEmpty();
    }
}
