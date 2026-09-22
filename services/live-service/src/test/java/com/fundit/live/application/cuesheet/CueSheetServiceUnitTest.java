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
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CueSheetServiceUnitTest {

    @Mock private LiveCueSheetRepository cueSheetRepository;
    @Mock private LiveSessionRepository sessionRepository;
    @Mock private AiClient aiClient;
    @Mock private AiProductContextAssembler productContextAssembler;
    @Mock private ApplicationEventPublisher eventPublisher;

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
    void 생성을_요청하면_GENERATING으로_저장하고_상품정보와_캠페인_현황을_실어_이벤트를_발행한다() {
        // given — AI를 직접 부르지 않는다. 최대 3분+ 걸릴 수 있어 요청 스레드를 잡으면 안 된다
        // (별도 스레드에서 처리하는 onCueSheetGenerationRequested로 넘긴다).
        givenOwnedSessionForUpdate();
        given(cueSheetRepository.findBySessionId(1L)).willReturn(Optional.empty());
        AiClient.PrepareRequest product = new AiClient.PrepareRequest("에어쿡 프로", "가전", "주방가전",
                null, UUID.randomUUID().toString(), List.of(), List.of());
        AiClient.FundingInfo funding = new AiClient.FundingInfo(null, 42, 5);
        given(productContextAssembler.forCueSheet(any()))
                .willReturn(new AiProductContextAssembler.CueSheetInput(product, funding));

        // when
        cueSheetService.requestGeneration(sellerId, liveId, "SCENARIO", 580, true,
                List.of("10년 무상 A/S"), "ACTIVE", List.of());

        // then
        ArgumentCaptor<LiveCueSheet> captor = ArgumentCaptor.forClass(LiveCueSheet.class);
        verify(cueSheetRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(GenerationStatus.GENERATING);

        ArgumentCaptor<CueSheetService.CueSheetGenerationRequested> eventCaptor =
                ArgumentCaptor.forClass(CueSheetService.CueSheetGenerationRequested.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().liveId()).isEqualTo(liveId);
        assertThat(eventCaptor.getValue().request().product()).isEqualTo(product);
        assertThat(eventCaptor.getValue().request().funding()).isEqualTo(funding);
        verify(aiClient, never()).requestCueSheet(any(), any());
    }

    @Test
    void 이벤트를_받으면_AI를_불러_COMPLETED로_저장한다() {
        // given — onCueSheetGenerationRequested는 별도 스레드에서 도니 Spring 이벤트 시스템 없이
        // 직접 호출해서 검증한다(package-private, LiveStreamService.markAiPrepared와 같은 방식).
        LiveCueSheet cueSheet = generating();
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(cueSheetRepository.findBySessionId(1L)).willReturn(Optional.of(cueSheet));
        AiClient.CueSheetRequest request = new AiClient.CueSheetRequest("SCENARIO", 580, true,
                List.of(), "ACTIVE", List.of(), null, null);
        given(aiClient.requestCueSheet(liveId.toString(), request)).willReturn("[{\"order\":1}]");

        // when
        cueSheetService.onCueSheetGenerationRequested(
                new CueSheetService.CueSheetGenerationRequested(liveId, request));

        // then
        assertThat(cueSheet.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(cueSheet.getSegments()).isEqualTo("[{\"order\":1}]");
    }

    @Test
    void AI_호출이_실패하면_FAILED로_저장한다() {
        // given
        LiveCueSheet cueSheet = generating();
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(cueSheetRepository.findBySessionId(1L)).willReturn(Optional.of(cueSheet));
        AiClient.CueSheetRequest request = new AiClient.CueSheetRequest("SCENARIO", 580, true,
                List.of(), "ACTIVE", List.of(), null, null);
        given(aiClient.requestCueSheet(liveId.toString(), request))
                .willThrow(new com.fundit.common.error.DependencyFailureException(
                        new RuntimeException("AI 서버 응답 없음")));

        // when
        cueSheetService.onCueSheetGenerationRequested(
                new CueSheetService.CueSheetGenerationRequested(liveId, request));

        // then
        assertThat(cueSheet.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(cueSheet.getFailureReason()).contains("AI 서버 응답 없음");
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
