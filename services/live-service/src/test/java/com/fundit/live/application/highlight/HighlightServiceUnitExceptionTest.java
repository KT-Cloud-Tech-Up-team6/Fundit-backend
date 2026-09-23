package com.fundit.live.application.highlight;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.chat.VodChatQueryService;
import com.fundit.live.application.project.ProjectContextClient;
import com.fundit.live.domain.ai.GenerationStatus;
import com.fundit.live.domain.highlight.HighlightKind;
import com.fundit.live.domain.highlight.LiveHighlight;
import com.fundit.live.domain.highlight.LiveHighlightRepository;
import com.fundit.live.domain.highlight.SceneLabel;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HighlightServiceUnitExceptionTest {

    @Mock private LiveHighlightRepository highlightRepository;
    @Mock private LiveSessionRepository sessionRepository;
    @Mock private AiClient aiClient;
    @Mock private ProjectContextClient projectContextClient;
    @Mock private VodChatQueryService vodChatQueryService;

    @InjectMocks private HighlightService highlightService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();
    private final UUID highlightId = UUID.randomUUID();

    private void givenOwned(String vodUrl) {
        given(sessionRepository.findOwned(liveId, sellerId)).willReturn(Optional.of(
                LiveSession.builder().id(1L).publicId(liveId).vodUrl(vodUrl).build()));
    }

    private LiveHighlight clip(GenerationStatus status, Long sessionId) {
        return LiveHighlight.builder()
                .id(1L).publicId(highlightId).sessionId(sessionId)
                .kind(HighlightKind.CLIP).sceneLabel(SceneLabel.DEMO)
                .startSec(320).endSec(400).isPublic(false)
                .generationStatus(status).viewCount(0).clickCount(0).build();
    }

    @Test
    void 다시보기가_없으면_생성을_요청하지_않는다() {
        // given — 판별할 영상이 없다
        givenOwned(null);

        // when & then
        assertThatThrownBy(() -> highlightService.requestGeneration(sellerId, liveId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
        verify(aiClient, never()).requestHighlights(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void 다시보기가_없으면_재생성도_거부한다() {
        // given — 같은 가드가 두 경로에 필요하다. 생성에만 붙이면 재생성으로 AI에 null이 간다.
        givenOwned(null);

        // when & then
        assertThatThrownBy(() -> highlightService.regenerate(sellerId, liveId, highlightId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
        verify(aiClient, never()).requestHighlights(anyString(), any(), any(), any(), any());
    }

    @Test
    void 생성_실패한_항목은_공개할_수_없다() {
        // given — 재생 불가한 클립이 소비자 화면에 올라간다
        givenOwned("https://vod");
        given(highlightRepository.findByPublicId(highlightId))
                .willReturn(Optional.of(clip(GenerationStatus.FAILED, 1L)));

        // when & then
        assertThatThrownBy(() -> highlightService.changeVisibility(sellerId, liveId, highlightId, true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
    }

    @Test
    void 다른_방송의_하이라이트는_조작할_수_없다() {
        // given — id를 넣어 남의 방송 자산을 건드리는 걸 막는다. 존재를 알리지 않으려 404다(S4·S10)
        givenOwned("https://vod");
        given(highlightRepository.findByPublicId(highlightId))
                .willReturn(Optional.of(clip(GenerationStatus.COMPLETED, 999L)));

        // when & then
        assertThatThrownBy(() -> highlightService.delete(sellerId, liveId, highlightId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        verify(highlightRepository, never()).deleteByPublicId(any());
    }

    @Test
    void 다른_방송의_하이라이트는_클릭도_셀_수_없다() {
        // given
        given(sessionRepository.findPublic(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(highlightRepository.findByPublicId(highlightId))
                .willReturn(Optional.of(clip(GenerationStatus.COMPLETED, 999L)));

        // when & then
        assertThatThrownBy(() -> highlightService.recordClick(liveId, highlightId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        verify(highlightRepository, never()).increaseClickCount(any());
    }

    @Test
    void 구간이_뒤집힌_수정은_거부한다() {
        // given — 클립 URL은 멀쩡한데 구간만 뒤집혀 재생이 깨진다
        givenOwned("https://vod");
        given(highlightRepository.findByPublicId(highlightId))
                .willReturn(Optional.of(clip(GenerationStatus.COMPLETED, 1L)));

        // when & then — 기존 startSec=320
        assertThatThrownBy(() -> highlightService.edit(sellerId, liveId, highlightId,
                null, 100, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void 끝이_없는_클립은_거부한다() {
        // given — DDL의 end_sec은 NULL 허용이라 DB가 막지 않는다.
        // 끝 없는 클립은 URL은 멀쩡한데 플레이어가 구간을 잡지 못한다
        given(sessionRepository.findOwnedAnyForUpdate(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(highlightRepository.countClips(1L)).willReturn(0L);

        // when & then
        assertThatThrownBy(() -> highlightService.applyGenerated(liveId, List.of(
                new HighlightService.GeneratedHighlight(null, HighlightKind.CLIP, SceneLabel.DEMO,
                        "시연", 10, null, "https://clip", "자막", GenerationStatus.COMPLETED))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void 종료_위치가_있는_마커는_거부한다() {
        // given — 마커는 시점이다
        given(sessionRepository.findOwnedAnyForUpdate(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(highlightRepository.countClips(1L)).willReturn(0L);

        // when & then
        assertThatThrownBy(() -> highlightService.applyGenerated(liveId, List.of(
                new HighlightService.GeneratedHighlight(null, HighlightKind.MARKER, SceneLabel.SPEC,
                        "스펙", 10, 40, null, null, GenerationStatus.COMPLETED))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void 음수_시작위치는_거부한다() {
        // given
        givenOwned("https://vod");
        given(highlightRepository.findByPublicId(highlightId))
                .willReturn(Optional.of(clip(GenerationStatus.COMPLETED, 1L)));

        // when & then
        assertThatThrownBy(() -> highlightService.edit(sellerId, liveId, highlightId,
                -1, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void 설정_중인_방송의_하이라이트는_공개_조회가_안_된다() {
        // given — DRAFT는 findPublic 쿼리에서 걸러진다. 조회수 카운터도 올라가면 안 된다
        given(sessionRepository.findPublic(liveId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> highlightService.findPublic(liveId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        verify(highlightRepository, never()).increaseViewCount(anyLong());
    }

    @Test
    void 설정_중인_방송은_클릭도_셀_수_없다() {
        // given
        given(sessionRepository.findPublic(liveId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> highlightService.recordClick(liveId, highlightId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
        verify(highlightRepository, never()).increaseClickCount(any());
    }
}
