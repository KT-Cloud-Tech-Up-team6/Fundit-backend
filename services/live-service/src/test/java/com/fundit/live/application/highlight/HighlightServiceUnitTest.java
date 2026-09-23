package com.fundit.live.application.highlight;

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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HighlightServiceUnitTest {

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

    /** 소비자 공개 경로는 DRAFT를 거르는 findPublic을 쓴다. */
    private void givenPublicSession() {
        given(sessionRepository.findPublic(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
    }

    /** AI 콜백은 행을 잠그고 읽는다 — 같은 결과가 두 번 와도 상한이 새면 안 된다. */
    private void givenSessionForCallback() {
        given(sessionRepository.findOwnedAnyForUpdate(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
    }

    private LiveHighlight clip(GenerationStatus status, boolean isPublic) {
        return LiveHighlight.builder()
                .id(1L).publicId(highlightId).sessionId(1L)
                .kind(HighlightKind.CLIP).sceneLabel(SceneLabel.DEMO)
                .startSec(320).endSec(400).isPublic(isPublic)
                .generationStatus(status).viewCount(0).clickCount(0).build();
    }

    private HighlightService.GeneratedHighlight generatedClip() {
        return new HighlightService.GeneratedHighlight(null, HighlightKind.CLIP, SceneLabel.DEMO,
                "실시간 시연", 320, 400, "https://clip", "자막", GenerationStatus.COMPLETED);
    }

    @Test
    void 자동_생성_결과는_비공개로_저장된다() {
        // given — 기본값을 뒤집으면 검수 전 내용이 그대로 샌다(PRD 6.6.3)
        givenSessionForCallback();
        given(highlightRepository.countClips(1L)).willReturn(0L);

        // when
        highlightService.applyGenerated(liveId, List.of(generatedClip()));

        // then
        ArgumentCaptor<LiveHighlight> captor = ArgumentCaptor.forClass(LiveHighlight.class);
        verify(highlightRepository).save(captor.capture());
        assertThat(captor.getValue().isPublic()).isFalse();
    }

    @Test
    void 클립이_이미_3개면_네_번째는_건너뛴다() {
        // given — 방송 1회당 최대 3개(PRD 6.6.3). 예외를 던지면 앞의 성공분까지 롤백된다
        givenSessionForCallback();
        given(highlightRepository.countClips(1L)).willReturn(3L);

        // when
        highlightService.applyGenerated(liveId, List.of(generatedClip()));

        // then
        verify(highlightRepository, times(0)).save(any());
    }

    @Test
    void 한_번에_4개가_와도_앞의_3개는_저장된다() {
        // given — 초과분에 예외를 던지면 @Transactional이 앞의 3개를 되돌린다(PRD 6.6.4)
        givenSessionForCallback();
        given(highlightRepository.countClips(1L)).willReturn(0L);

        // when
        highlightService.applyGenerated(liveId,
                List.of(generatedClip(), generatedClip(), generatedClip(), generatedClip()));

        // then
        verify(highlightRepository, times(3)).save(any());
    }

    @Test
    void 마커는_개수_제한이_없다() {
        // given
        givenSessionForCallback();
        given(highlightRepository.countClips(1L)).willReturn(3L);
        var marker = new HighlightService.GeneratedHighlight(null, HighlightKind.MARKER,
                SceneLabel.SPEC, "핵심 스펙", 120, null, null, null, GenerationStatus.COMPLETED);

        // when
        highlightService.applyGenerated(liveId, List.of(marker, marker, marker, marker, marker));

        // then
        verify(highlightRepository, times(5)).save(any());
    }

    @Test
    void 재생성_결과는_기존_행을_갱신한다() {
        // given — 새 행으로 저장하면 원래 행이 GENERATING으로 남고 클립 수가 상한에 걸린다
        givenSessionForCallback();
        given(highlightRepository.countClips(1L)).willReturn(1L);
        LiveHighlight existing = clip(GenerationStatus.GENERATING, false);
        given(highlightRepository.findByPublicId(highlightId)).willReturn(Optional.of(existing));

        // when
        highlightService.applyGenerated(liveId, List.of(new HighlightService.GeneratedHighlight(
                highlightId, HighlightKind.CLIP, SceneLabel.DEMO, "새 제목", 10, 30,
                "https://new", "새 자막", GenerationStatus.COMPLETED)));

        // then
        assertThat(existing.getTitle()).isEqualTo("새 제목");
        assertThat(existing.getGenerationStatus()).isEqualTo(GenerationStatus.COMPLETED);
        verify(highlightRepository).save(existing);
    }

    @Test
    void 생성_요청은_채팅과_상품명을_같이_보낸다() {
        // given — 방송 시작·종료·프로젝트가 다 있으면 chats·productName을 채워 보낸다(AI팀 요청)
        Instant startedAt = Instant.parse("2026-09-23T10:00:00Z");
        Instant endedAt = startedAt.plusSeconds(120);
        UUID projectId = UUID.randomUUID();
        given(sessionRepository.findOwned(liveId, sellerId)).willReturn(Optional.of(
                LiveSession.builder().id(1L).publicId(liveId).projectId(projectId).vodUrl("https://vod")
                        .actualStartAt(startedAt).actualEndAt(endedAt).build()));
        var chat = com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity.builder()
                .id(9L).senderId(UUID.randomUUID()).content("언제 끝나요?").sentAt(startedAt.plusSeconds(60)).build();
        given(vodChatQueryService.findByRange(liveId, 0, 120)).willReturn(
                new VodChatQueryService.VodChat(startedAt, List.of(chat)));
        given(projectContextClient.find(projectId)).willReturn(Optional.of(
                new ProjectContextClient.ProjectContext("에어쿡 프로", "가전", "주방가전", List.of(), 50, 5, null)));

        // when
        highlightService.requestGeneration(sellerId, liveId);

        // then
        ArgumentCaptor<List<AiClient.CommentInput>> chatsCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiClient).requestHighlights(org.mockito.ArgumentMatchers.eq(liveId.toString()),
                org.mockito.ArgumentMatchers.eq("https://vod"), org.mockito.ArgumentMatchers.isNull(),
                chatsCaptor.capture(), org.mockito.ArgumentMatchers.eq("에어쿡 프로"));
        assertThat(chatsCaptor.getValue()).hasSize(1);
        assertThat(chatsCaptor.getValue().getFirst().commentId()).isEqualTo("9");
        assertThat(chatsCaptor.getValue().getFirst().atMs()).isEqualTo(60_000L);
    }

    @Test
    void 재생성하면_공개가_해제된다() {
        // given — 재생성 중인 항목이 공개된 채면 소비자가 옛 클립을 본다
        givenOwned("https://vod");
        LiveHighlight published = clip(GenerationStatus.COMPLETED, true);
        given(highlightRepository.findByPublicId(highlightId)).willReturn(Optional.of(published));

        // when
        highlightService.regenerate(sellerId, liveId, highlightId);

        // then
        assertThat(published.isPublic()).isFalse();
        assertThat(published.getGenerationStatus()).isEqualTo(GenerationStatus.GENERATING);
        verify(aiClient).requestHighlights(liveId.toString(), "https://vod", highlightId, List.of(), null);
    }

    @Test
    void 소비자_공개_조회가_조회수를_올린다() {
        // given — 소비자 화면이 하이라이트를 보려면 어차피 이 API를 부른다
        givenPublicSession();
        given(highlightRepository.findPublicBySessionId(1L)).willReturn(List.of());

        // when
        highlightService.findPublic(liveId);

        // then
        verify(highlightRepository).increaseViewCount(1L);
    }

    @Test
    void 클릭은_소속을_확인한_뒤_센다() {
        // given
        givenPublicSession();
        given(highlightRepository.findByPublicId(highlightId))
                .willReturn(Optional.of(clip(GenerationStatus.COMPLETED, true)));

        // when
        highlightService.recordClick(liveId, highlightId);

        // then
        verify(highlightRepository).increaseClickCount(highlightId);
    }
}
