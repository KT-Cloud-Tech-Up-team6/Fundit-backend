package com.fundit.live.application.highlight;

import com.fundit.common.error.BusinessException;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.highlight.LiveHighlightJpaEntity;
import com.fundit.live.infrastructure.persistence.highlight.LiveHighlightJpaRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HighlightServiceUnitTest {

    @Mock private LiveHighlightJpaRepository highlightRepository;
    @Mock private LiveSessionRepository sessionRepository;
    @Mock private AiClient aiClient;

    @InjectMocks private HighlightService highlightService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();
    private final UUID highlightId = UUID.randomUUID();

    private void givenOwned(String vodUrl) {
        given(sessionRepository.findOwned(liveId, sellerId)).willReturn(Optional.of(
                LiveSession.builder().id(1L).publicId(liveId).vodUrl(vodUrl).build()));
    }

    private LiveHighlightJpaEntity clip(String status, boolean isPublic) {
        return LiveHighlightJpaEntity.builder()
                .id(1L).publicId(highlightId).sessionId(1L)
                .kind(LiveHighlightJpaEntity.KIND_CLIP).sceneLabel("DEMO")
                .startSec(320).endSec(400).isPublic(isPublic)
                .generationStatus(status).viewCount(0).clickCount(0).build();
    }

    private HighlightService.GeneratedHighlight generatedClip() {
        return new HighlightService.GeneratedHighlight(LiveHighlightJpaEntity.KIND_CLIP, "DEMO",
                "실시간 시연", 320, 400, "https://clip", "자막",
                LiveHighlightJpaEntity.STATUS_COMPLETED);
    }

    @Test
    void 다시보기가_없으면_생성을_요청하지_않는다() {
        // given — 판별할 영상이 없다
        givenOwned(null);

        // when & then
        assertThatThrownBy(() -> highlightService.requestGeneration(sellerId, liveId))
                .isInstanceOf(BusinessException.class);
        verify(aiClient, never()).requestHighlights(anyString(), anyString());
    }

    @Test
    void 자동_생성_결과는_비공개로_저장된다() {
        // given — 기본값을 뒤집으면 검수 전 내용이 그대로 샌다(PRD 6.6.3)
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(highlightRepository.countBySessionIdAndKind(1L, LiveHighlightJpaEntity.KIND_CLIP))
                .willReturn(0L);

        // when
        highlightService.applyGenerated(liveId, List.of(generatedClip()));

        // then
        var captor = org.mockito.ArgumentCaptor.forClass(LiveHighlightJpaEntity.class);
        verify(highlightRepository).save(captor.capture());
        assertThat(captor.getValue().isPublic()).isFalse();
    }

    @Test
    void 클립이_이미_3개면_네_번째는_거부한다() {
        // given — 방송 1회당 최대 3개(PRD 6.6.3). AI가 더 보내도 서버가 막는다
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(highlightRepository.countBySessionIdAndKind(1L, LiveHighlightJpaEntity.KIND_CLIP))
                .willReturn(3L);

        // when & then
        assertThatThrownBy(() -> highlightService.applyGenerated(liveId, List.of(generatedClip())))
                .isInstanceOf(BusinessException.class);
        verify(highlightRepository, never()).save(any());
    }

    @Test
    void 한_번에_4개가_와도_네_번째에서_막힌다() {
        // given — 배치 안에서도 누적으로 센다
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(highlightRepository.countBySessionIdAndKind(1L, LiveHighlightJpaEntity.KIND_CLIP))
                .willReturn(0L);

        // when & then
        assertThatThrownBy(() -> highlightService.applyGenerated(liveId,
                List.of(generatedClip(), generatedClip(), generatedClip(), generatedClip())))
                .isInstanceOf(BusinessException.class);
        verify(highlightRepository, times(3)).save(any());
    }

    @Test
    void 마커는_개수_제한이_없다() {
        // given
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(highlightRepository.countBySessionIdAndKind(1L, LiveHighlightJpaEntity.KIND_CLIP))
                .willReturn(3L);
        var marker = new HighlightService.GeneratedHighlight(LiveHighlightJpaEntity.KIND_MARKER,
                "SPEC", "핵심 스펙", 120, null, null, null, LiveHighlightJpaEntity.STATUS_COMPLETED);

        // when
        highlightService.applyGenerated(liveId, List.of(marker, marker, marker, marker, marker));

        // then
        verify(highlightRepository, times(5)).save(any());
    }

    @Test
    void 생성_실패한_항목은_공개할_수_없다() {
        // given — 재생 불가한 클립이 소비자 화면에 올라간다
        givenOwned("https://vod");
        given(highlightRepository.findByPublicId(highlightId))
                .willReturn(Optional.of(clip(LiveHighlightJpaEntity.STATUS_FAILED, false)));

        // when & then
        assertThatThrownBy(() -> highlightService.changeVisibility(sellerId, liveId, highlightId, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 재생성하면_공개가_해제된다() {
        // given — 재생성 중인 항목이 공개된 채면 소비자가 옛 클립을 본다
        givenOwned("https://vod");
        LiveHighlightJpaEntity published = clip(LiveHighlightJpaEntity.STATUS_COMPLETED, true);
        given(highlightRepository.findByPublicId(highlightId)).willReturn(Optional.of(published));

        // when
        highlightService.regenerate(sellerId, liveId, highlightId);

        // then
        assertThat(published.isPublic()).isFalse();
        assertThat(published.getGenerationStatus()).isEqualTo(LiveHighlightJpaEntity.STATUS_GENERATING);
    }

    @Test
    void 다른_방송의_하이라이트는_조작할_수_없다() {
        // given — id를 넣어 남의 방송 자산을 건드리는 걸 막는다(S4)
        givenOwned("https://vod");
        LiveHighlightJpaEntity other = LiveHighlightJpaEntity.builder()
                .id(9L).publicId(highlightId).sessionId(999L)
                .kind(LiveHighlightJpaEntity.KIND_CLIP).sceneLabel("DEMO").startSec(1)
                .generationStatus(LiveHighlightJpaEntity.STATUS_COMPLETED).build();
        given(highlightRepository.findByPublicId(highlightId)).willReturn(Optional.of(other));

        // when & then
        assertThatThrownBy(() -> highlightService.delete(sellerId, liveId, highlightId))
                .isInstanceOf(BusinessException.class);
        verify(highlightRepository, never()).delete(any());
    }

    @Test
    void 소비자_공개_조회가_조회수를_올린다() {
        // given — 소비자 화면이 하이라이트를 보려면 어차피 이 API를 부른다
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(highlightRepository.findBySessionIdAndIsPublicTrueOrderByStartSecAsc(1L))
                .willReturn(List.of());

        // when
        highlightService.findPublic(liveId);

        // then
        verify(highlightRepository).increaseViewCount(1L);
    }
}
