package com.fundit.live.application.cuesheet;

import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.ai.AiProductContextAssembler;
import com.fundit.live.domain.ai.GenerationStatus;
import com.fundit.live.domain.cuesheet.LiveCueSheet;
import com.fundit.live.domain.cuesheet.LiveCueSheetRepository;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CueSheetServiceUnitTest {

    @Mock private LiveCueSheetRepository cueSheetRepository;
    @Mock private LiveSessionRepository sessionRepository;
    @Mock private AiClient aiClient;
    @Mock private AiProductContextAssembler productContextAssembler;

    @InjectMocks private CueSheetService cueSheetService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    /** 생성 요청은 행을 잠그고 읽는다 — 더블클릭으로 AI 작업이 두 번 돌면 안 된다. */
    private void givenOwnedSessionForUpdate() {
        given(sessionRepository.findOwnedForUpdate(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
    }

    private LiveCueSheet generating() {
        return LiveCueSheet.requestGeneration(1L, "SCENARIO", 580);
    }

    @Test
    void 생성을_요청하면_GENERATING으로_저장하고_상품정보를_실어_AI를_부른다() {
        // given
        givenOwnedSessionForUpdate();
        given(cueSheetRepository.findBySessionId(1L)).willReturn(Optional.empty());
        AiClient.PrepareRequest product = new AiClient.PrepareRequest("에어쿡 프로", "가전", "주방가전",
                null, UUID.randomUUID().toString(), List.of(), List.of());
        given(productContextAssembler.assemble(any())).willReturn(product);

        // when
        cueSheetService.requestGeneration(sellerId, liveId, "SCENARIO", 580, true,
                List.of("10년 무상 A/S"), "ACTIVE", List.of());

        // then
        ArgumentCaptor<LiveCueSheet> captor = ArgumentCaptor.forClass(LiveCueSheet.class);
        verify(cueSheetRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GenerationStatus.GENERATING);
        ArgumentCaptor<AiClient.CueSheetRequest> requestCaptor = ArgumentCaptor.forClass(AiClient.CueSheetRequest.class);
        verify(aiClient).requestCueSheet(anyString(), requestCaptor.capture());
        assertThat(requestCaptor.getValue().product()).isEqualTo(product);
    }

    @Test
    void AI_결과가_오면_COMPLETED로_바뀐다() {
        // given
        LiveCueSheet cueSheet = generating();
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(cueSheetRepository.findBySessionId(1L)).willReturn(Optional.of(cueSheet));

        // when
        cueSheetService.applyResult(liveId, "COMPLETED", "[{\"order\":1}]", null);

        // then
        assertThat(cueSheet.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(cueSheet.getSegments()).isEqualTo("[{\"order\":1}]");
        verify(cueSheetRepository).save(cueSheet);
    }

    @Test
    void 생성이_끝난_큐시트는_수정된다() {
        // given — 수정은 JSONB 문서 통째 교체라 잠그지 않는다(last-write-wins가 정상)
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        LiveCueSheet cueSheet = generating();
        cueSheet.complete("[{\"order\":1}]");
        given(cueSheetRepository.findBySessionId(1L)).willReturn(Optional.of(cueSheet));

        // when
        cueSheetService.replaceSegments(sellerId, liveId, "[{\"order\":2}]");

        // then
        assertThat(cueSheet.getSegments()).isEqualTo("[{\"order\":2}]");
        verify(cueSheetRepository).save(cueSheet);
    }
}
