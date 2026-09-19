package com.fundit.live.application.cuesheet;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.ai.GenerationStatus;
import com.fundit.live.domain.cuesheet.LiveCueSheet;
import com.fundit.live.domain.cuesheet.LiveCueSheetRepository;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CueSheetServiceUnitExceptionTest {

    @Mock private LiveCueSheetRepository cueSheetRepository;
    @Mock private LiveSessionRepository sessionRepository;
    @Mock private AiClient aiClient;

    @InjectMocks private CueSheetService cueSheetService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    private void givenOwnedSession() {
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
    }

    private LiveCueSheet givenGeneratingCueSheet() {
        LiveCueSheet cueSheet = LiveCueSheet.requestGeneration(1L, "SCENARIO", 580);
        given(cueSheetRepository.findBySessionId(1L)).willReturn(Optional.of(cueSheet));
        return cueSheet;
    }

    private void givenSessionByPublicId() {
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
    }

    @Test
    void 방송_길이가_10분을_넘으면_400이고_AI를_부르지_않는다() {
        // given & when & then — 요구사항정의서 6.2.3
        assertThatThrownBy(() -> cueSheetService.requestGeneration(sellerId, liveId, "SCENARIO", 601,
                false, List.of(), null, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
        verify(aiClient, never()).requestCueSheet(anyString(), any());
    }

    @Test
    void 이미_생성_중이면_409다() {
        // given — 두 번 돌면 결과가 서로 덮어써 어느 쪽이 남는지 알 수 없다
        givenOwnedSession();
        givenGeneratingCueSheet();

        // when & then
        assertThatThrownBy(() -> cueSheetService.requestGeneration(sellerId, liveId, "SCENARIO", 580,
                false, List.of(), null, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.CONFLICT);
    }

    @Test
    void 구간이_비어_있으면_저장하지_않는다() {
        // given — 외부 응답을 그대로 신뢰하지 않는다(security.md S7)
        givenSessionByPublicId();
        LiveCueSheet cueSheet = givenGeneratingCueSheet();

        // when & then
        assertThatThrownBy(() -> cueSheetService.applyResult(liveId, "COMPLETED", "  ", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
        assertThat(cueSheet.getStatus()).isEqualTo(GenerationStatus.GENERATING);
    }

    @Test
    void 모르는_상태는_실패로_굳히지_않는다() {
        // given — "PROCESSING"을 FAILED로 저장하면 되돌릴 경로가 없다
        givenSessionByPublicId();
        LiveCueSheet cueSheet = givenGeneratingCueSheet();

        // when & then
        assertThatThrownBy(() -> cueSheetService.applyResult(liveId, "PROCESSING", null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
        assertThat(cueSheet.getStatus()).isEqualTo(GenerationStatus.GENERATING);
        verify(cueSheetRepository, never()).save(any());
    }

    @Test
    void 생성_중인_큐시트는_수정할_수_없다() {
        // given
        givenOwnedSession();
        givenGeneratingCueSheet();

        // when & then
        assertThatThrownBy(() -> cueSheetService.replaceSegments(sellerId, liveId, "[]"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.CONFLICT);
    }
}
