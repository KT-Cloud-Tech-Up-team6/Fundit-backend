package com.fundit.live.infrastructure.ai;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ai.AiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void 큐시트_요청은_캔_구간을_즉시_돌려준다() {
        // given — 콜백이 사라졌으니(동기 전환) 스텁도 완료 흐름을 실제로 타야 로컬에서
        // GENERATING에 영원히 머물지 않는다
        AiClient.CueSheetRequest request = new AiClient.CueSheetRequest(
                "SCENARIO", 580, false, List.of(), null, List.of(), null, null);

        // when
        String segments = new StubAiClient().requestCueSheet("live", request);

        // then
        assertThat(segments).contains("\"id\":\"stub-0\"");
    }

    @Test
    void 큐시트_요청은_매직_톤값이면_실패를_흉내낸다() {
        // given — QA/dev에서 FAILED 상태를 재현할 방법이 없었다(FE 요청). prod는 이 스텁 자체가
        // 안 뜨므로 운영 코드 경로에는 영향이 없다.
        AiClient.CueSheetRequest request = new AiClient.CueSheetRequest(
                "SCENARIO", 580, false, List.of(), "QA_FORCE_FAIL", List.of(), null, null);

        // when & then
        assertThatThrownBy(() -> new StubAiClient().requestCueSheet("live", request))
                .isInstanceOf(DependencyFailureException.class);
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
