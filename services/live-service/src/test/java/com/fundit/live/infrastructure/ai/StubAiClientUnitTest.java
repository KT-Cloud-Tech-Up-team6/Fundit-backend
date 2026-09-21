package com.fundit.live.infrastructure.ai;

import com.fundit.live.application.ai.AiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StubAiClientUnitTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(StubAiClient.class);

    @Test
    void 모드가_없으면_스텁이_뜨지_않는다() {
        // given & when & then — matchIfMissing=true였을 때 운영에서 스텁이 조용히 선택돼
        // 큐시트·추천답변이 "[stub] ..." 문자열로 "성공"했다. prod yml에는 live.ai.mode가 아예 없었다.
        runner.run(context -> assertThat(context).doesNotHaveBean(StubAiClient.class));
    }

    @Test
    void 모드가_stub이면_뜬다() {
        // given & when & then — 로컬·개발은 application.yml의 기본값 stub으로 계속 동작한다
        runner.withPropertyValues("live.ai.mode=stub")
                .run(context -> assertThat(context).hasSingleBean(StubAiClient.class));
    }

    @Test
    void 댓글_배치는_전부_무시_처리로_흉내낸다() {
        // given — 답변이 있는 척하면 화면 검증이 어긋난다
        var comments = List.of(new AiClient.CommentInput("c1", "질문", 0, java.util.UUID.randomUUID()));

        // when
        AiClient.CommentBatchResult result = new StubAiClient().submitComments("live", comments);

        // then
        assertThat(result.questions()).isEmpty();
        assertThat(result.ignored()).hasSize(1);
    }
}
