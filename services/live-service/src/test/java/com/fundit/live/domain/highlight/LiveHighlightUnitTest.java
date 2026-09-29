package com.fundit.live.domain.highlight;

import com.fundit.live.domain.ai.GenerationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiveHighlightUnitTest {

    private static LiveHighlight marker(GenerationStatus status) {
        return LiveHighlight.generated(1L, HighlightKind.MARKER, SceneLabel.DEMO, "시연", 120, null,
                null, null, null, status);
    }

    private static LiveHighlight clip(GenerationStatus status) {
        return LiveHighlight.generated(1L, HighlightKind.CLIP, SceneLabel.DEMO, "시연", 120, 200,
                "https://clip", "https://thumb", "자막", status);
    }

    @Nested
    @DisplayName("생성 시 공개 여부")
    class 생성 {

        @Test
        void 완료된_마커는_바로_공개된다() {
            // given — 챕터를 공개할 판매자 화면이 없어 비공개면 다시보기 구간 탐색이 비어 있다
            GenerationStatus status = GenerationStatus.COMPLETED;

            // when
            LiveHighlight highlight = marker(status);

            // then
            assertThat(highlight.isPublic()).isTrue();
        }

        @Test
        void 완료된_클립은_비공개로_시작한다() {
            // given — 판매자가 확정해야 소비자에게 보인다
            GenerationStatus status = GenerationStatus.COMPLETED;

            // when
            LiveHighlight highlight = clip(status);

            // then
            assertThat(highlight.isPublic()).isFalse();
        }

        @Test
        void 실패한_마커는_비공개다() {
            // given — 재생할 구간이 없다
            GenerationStatus status = GenerationStatus.FAILED;

            // when
            LiveHighlight highlight = marker(status);

            // then
            assertThat(highlight.isPublic()).isFalse();
        }
    }

    @Nested
    @DisplayName("재생성")
    class 재생성 {

        @Test
        void 재생성_중에는_마커도_비공개다() {
            // given
            LiveHighlight highlight = marker(GenerationStatus.COMPLETED);

            // when
            highlight.markRegenerating();

            // then
            assertThat(highlight.isPublic()).isFalse();
        }

        @Test
        void 완료로_재생성된_마커는_다시_공개된다() {
            // given
            LiveHighlight highlight = marker(GenerationStatus.COMPLETED);
            highlight.markRegenerating();

            // when
            highlight.applyRegenerated(SceneLabel.SPEC, "스펙", 300, null, null, null, null, GenerationStatus.COMPLETED);

            // then
            assertThat(highlight.isPublic()).isTrue();
        }

        @Test
        void 재생성된_클립은_공개됐던_것도_비공개로_돌아간다() {
            // given — 검토 전 새 클립이 그대로 새면 안 된다
            LiveHighlight highlight = clip(GenerationStatus.COMPLETED);
            highlight.changeVisibility(true);

            // when
            highlight.applyRegenerated(SceneLabel.SPEC, "스펙", 300, 380, "https://new", null, "자막",
                    GenerationStatus.COMPLETED);

            // then
            assertThat(highlight.isPublic()).isFalse();
        }
    }
}
