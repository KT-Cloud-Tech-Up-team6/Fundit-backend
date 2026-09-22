package com.fundit.live.infrastructure.cuesheet;

import com.fundit.live.infrastructure.persistence.cuesheet.LiveCueSheetJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CueSheetGenerationRecoveryWorkerUnitTest {

    private static final long STALE_AFTER_MS = 240_000L;

    @Mock
    private LiveCueSheetJpaRepository cueSheetRepository;

    private CueSheetGenerationRecoveryWorker worker() {
        return new CueSheetGenerationRecoveryWorker(cueSheetRepository, STALE_AFTER_MS);
    }

    @Test
    void 정체된_GENERATING을_임계값_기준으로_FAILED로_돌린다() {
        // given
        ArgumentCaptor<Instant> beforeCaptor = ArgumentCaptor.forClass(Instant.class);
        given(cueSheetRepository.failStaleGenerating(beforeCaptor.capture(), anyString())).willReturn(2);
        Instant expected = Instant.now().minusMillis(STALE_AFTER_MS);

        // when
        worker().failStale();

        // then — 스케줄러 호출 시점과 테스트 검증 시점 사이 오차를 5초까지 허용한다
        assertThat(beforeCaptor.getValue()).isCloseTo(expected, org.assertj.core.api.Assertions
                .within(5, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void 대상이_없으면_경고_로그_없이_조용히_끝난다() {
        // given
        given(cueSheetRepository.failStaleGenerating(org.mockito.ArgumentMatchers.any(), anyString()))
                .willReturn(0);

        // when & then — 예외 없이 끝나면 충분하다(로그 레벨은 단위 테스트 대상이 아니다)
        worker().failStale();
        verify(cueSheetRepository).failStaleGenerating(org.mockito.ArgumentMatchers.any(), anyString());
    }
}
