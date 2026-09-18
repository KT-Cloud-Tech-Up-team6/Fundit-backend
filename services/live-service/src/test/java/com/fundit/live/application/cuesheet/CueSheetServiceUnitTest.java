package com.fundit.live.application.cuesheet;

import com.fundit.common.error.BusinessException;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.cuesheet.LiveCueSheetJpaEntity;
import com.fundit.live.infrastructure.persistence.cuesheet.LiveCueSheetJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class CueSheetServiceUnitTest {

    @Mock private LiveCueSheetJpaRepository cueSheetRepository;
    @Mock private LiveSessionRepository sessionRepository;
    @Mock private AiClient aiClient;

    @InjectMocks private CueSheetService cueSheetService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    private void givenOwnedSession() {
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
    }

    @Test
    void 생성을_요청하면_GENERATING으로_저장하고_AI를_부른다() {
        // given
        givenOwnedSession();
        given(cueSheetRepository.findById(1L)).willReturn(Optional.empty());

        // when
        cueSheetService.requestGeneration(sellerId, liveId, "SCENARIO", 580, true,
                List.of("10년 무상 A/S"), "ACTIVE", List.of());

        // then
        ArgumentCaptor<LiveCueSheetJpaEntity> captor = ArgumentCaptor.forClass(LiveCueSheetJpaEntity.class);
        verify(cueSheetRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LiveCueSheetJpaEntity.STATUS_GENERATING);
        verify(aiClient).requestCueSheet(anyString(), any());
    }

    @Test
    void 방송_길이가_10분을_넘으면_400이고_AI를_부르지_않는다() {
        // given & when & then — 요구사항정의서 6.2.3
        assertThatThrownBy(() -> cueSheetService.requestGeneration(sellerId, liveId, "SCENARIO", 601,
                false, List.of(), null, List.of()))
                .isInstanceOf(BusinessException.class);
        verify(aiClient, never()).requestCueSheet(anyString(), any());
    }

    @Test
    void 이미_생성_중이면_409다() {
        // given — 두 번 돌면 결과가 서로 덮어써 어느 쪽이 남는지 알 수 없다
        givenOwnedSession();
        given(cueSheetRepository.findById(1L)).willReturn(Optional.of(LiveCueSheetJpaEntity.builder()
                .sessionId(1L).mode("SCENARIO").status(LiveCueSheetJpaEntity.STATUS_GENERATING)
                .targetDurationSec(580).build()));

        // when & then
        assertThatThrownBy(() -> cueSheetService.requestGeneration(sellerId, liveId, "SCENARIO", 580,
                false, List.of(), null, List.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void AI_결과가_오면_COMPLETED로_바뀐다() {
        // given
        LiveCueSheetJpaEntity cueSheet = LiveCueSheetJpaEntity.builder()
                .sessionId(1L).mode("SCENARIO").status(LiveCueSheetJpaEntity.STATUS_GENERATING)
                .targetDurationSec(580).build();
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(cueSheetRepository.findById(1L)).willReturn(Optional.of(cueSheet));

        // when
        cueSheetService.applyResult(liveId, "COMPLETED", "[{\"order\":1}]", null);

        // then
        assertThat(cueSheet.getStatus()).isEqualTo(LiveCueSheetJpaEntity.STATUS_COMPLETED);
        assertThat(cueSheet.getSegments()).isEqualTo("[{\"order\":1}]");
    }

    @Test
    void 구간이_비어_있으면_저장하지_않는다() {
        // given — 외부 응답을 그대로 신뢰하지 않는다(security.md S7)
        LiveCueSheetJpaEntity cueSheet = LiveCueSheetJpaEntity.builder()
                .sessionId(1L).mode("SCENARIO").status(LiveCueSheetJpaEntity.STATUS_GENERATING)
                .targetDurationSec(580).build();
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(cueSheetRepository.findById(1L)).willReturn(Optional.of(cueSheet));

        // when & then
        assertThatThrownBy(() -> cueSheetService.applyResult(liveId, "COMPLETED", "  ", null))
                .isInstanceOf(BusinessException.class);
        assertThat(cueSheet.getStatus()).isEqualTo(LiveCueSheetJpaEntity.STATUS_GENERATING);
    }

    @Test
    void 생성_중인_큐시트는_수정할_수_없다() {
        // given
        givenOwnedSession();
        given(cueSheetRepository.findById(1L)).willReturn(Optional.of(LiveCueSheetJpaEntity.builder()
                .sessionId(1L).mode("SCENARIO").status(LiveCueSheetJpaEntity.STATUS_GENERATING)
                .targetDurationSec(580).build()));

        // when & then
        assertThatThrownBy(() -> cueSheetService.replaceSegments(sellerId, liveId, "[]"))
                .isInstanceOf(BusinessException.class);
    }
}
