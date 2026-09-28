package com.fundit.live.presentation.controller;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.live.application.chat.ChatTokenService;
import com.fundit.live.application.chat.VodChatQueryService;
import com.fundit.live.application.like.LiveLikeService;
import com.fundit.live.application.session.LiveCreateService;
import com.fundit.live.application.session.LivePlaybackService;
import com.fundit.live.application.session.LiveQueryService;
import com.fundit.live.application.session.LiveSettingsService;
import com.fundit.live.application.session.LiveStreamService;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.presentation.GlobalExceptionHandler;
import com.fundit.live.presentation.dto.LiveDetailResponse;
import com.fundit.live.presentation.dto.LiveStatusCountsResponse;
import com.fundit.live.presentation.dto.LiveSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LiveController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class LiveControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private LiveCreateService liveCreateService;
    @MockitoBean private LiveSettingsService liveSettingsService;
    @MockitoBean private LiveStreamService liveStreamService;
    @MockitoBean private LiveQueryService liveQueryService;
    @MockitoBean private LivePlaybackService livePlaybackService;
    @MockitoBean private ChatTokenService chatTokenService;
    @MockitoBean private LiveLikeService liveLikeService;
    @MockitoBean private VodChatQueryService vodChatQueryService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void LIVE를_생성하면_201과_DRAFT를_돌려준다() throws Exception {
        // given
        LiveSession created = LiveSession.create(1L, UUID.randomUUID());
        when(liveCreateService.create(any(), any())).thenReturn(created);

        // when & then
        mockMvc.perform(post("/api/v1/lives")
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.liveId").value(created.getPublicId().toString()));
    }


    @Test
    void 소비자_목록은_인증_없이_조회된다() throws Exception {
        // given — 방송 자체가 공개다
        when(liveQueryService.findPublic(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/lives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void 목록_응답에_title이_아니라_introText가_실린다() throws Exception {
        // given — 요구사항정의서 6.2.4.1에 LIVE 제목 입력이 없다. 카드 문구는 소개 문구다.
        LiveSessionJpaEntity entity = LiveSessionJpaEntity.builder()
                .publicId(UUID.randomUUID())
                .projectId(UUID.randomUUID())
                .channelId(1L)
                .introText("5분만에 알아보는 신제품")
                .status(LiveStatus.LIVE)
                .likeCount(3)
                .build();
        when(liveQueryService.findLiveBanner()).thenReturn(List.of(LiveSummaryResponse.from(entity)));

        // when & then
        mockMvc.perform(get("/api/v1/lives/banner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].introText").value("5분만에 알아보는 신제품"))
                .andExpect(jsonPath("$[0].title").doesNotExist());
    }

    @Test
    void 내_목록은_로그인_사용자_기준으로_조회한다() throws Exception {
        // given
        when(liveQueryService.findMine(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/lives/mine")
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk());
    }

    @Test
    void 상태별_건수를_조회한다() throws Exception {
        // given
        when(liveQueryService.countMineByStatus(any()))
                .thenReturn(new LiveStatusCountsResponse(2, 1, 1, 3, 0));

        // when & then
        mockMvc.perform(get("/api/v1/lives/status-counts")
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft").value(2))
                .andExpect(jsonPath("$.ended").value(3));
    }

    @Test
    void 단건_상세를_조회한다() throws Exception {
        // given
        UUID liveId = UUID.randomUUID();
        when(liveQueryService.findOwnedDetail(any(), any())).thenReturn(
                new LiveDetailResponse(liveId, "LIVE", UUID.randomUUID(), "테크·가전", "생활가전",
                        "소개", null, null, 0, java.time.Instant.now(), 7, 90L));

        // when & then
        mockMvc.perform(get("/api/v1/lives/{liveId}", liveId)
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerCount").value(7))
                .andExpect(jsonPath("$.elapsedSeconds").value(90));
    }

    @Test
    void 설정을_저장하면_상태와_예정시각을_돌려준다() throws Exception {
        // given
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        session.updateSettings("테크·가전", "생활가전", "소개", null,
                java.time.Instant.parse("2026-09-10T11:00:00Z"));
        when(liveSettingsService.update(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(session);

        // when & then
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/lives/{liveId}/settings", UUID.randomUUID())
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "category": { "major": "테크·가전", "minor": "생활가전" },
                                  "introText": "소개", "scheduledStartAt": "2026-09-10T11:00:00Z" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.scheduledStartAt").exists());
    }


    @Test
    void 시작하면_LIVE_상태를_돌려준다() throws Exception {
        // given
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        session.start(java.time.Instant.parse("2026-09-10T11:00:00Z"), "arn:chat");
        when(liveStreamService.start(any(), any())).thenReturn(session);

        // when & then
        mockMvc.perform(post("/api/v1/lives/{liveId}/start", UUID.randomUUID())
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.actualStartAt").exists());
    }

    @Test
    void 종료하면_ENDED_상태를_돌려준다() throws Exception {
        // given
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        session.start(java.time.Instant.parse("2026-09-10T11:00:00Z"), "arn:chat");
        session.end(java.time.Instant.parse("2026-09-10T11:10:00Z"));
        when(liveStreamService.end(any(), any())).thenReturn(session);

        // when & then
        mockMvc.perform(post("/api/v1/lives/{liveId}/end", UUID.randomUUID())
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.actualEndAt").exists());
    }

    @Test
    void 채팅_토큰은_capabilities를_함께_내려준다() throws Exception {
        // given
        when(chatTokenService.issue(any(), any())).thenReturn(
                new ChatTokenService.ChatToken("tok", "arn:room", List.of("SEND_MESSAGE")));

        // when & then
        mockMvc.perform(post("/api/v1/lives/{liveId}/chat/token", UUID.randomUUID())
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok"))
                .andExpect(jsonPath("$.capabilities[0]").value("SEND_MESSAGE"));
    }

    @Test
    void 시청_정보는_인증_없이_조회된다() throws Exception {
        // given — 방송 자체가 공개다
        when(livePlaybackService.playback(any())).thenReturn(
                new LivePlaybackService.Playback(UUID.randomUUID(), "LIVE", "https://play",
                        UUID.randomUUID(), 3, null));

        // when & then
        mockMvc.perform(get("/api/v1/lives/{liveId}/playback", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("LIVE"));
    }

    @Test
    void 다시보기_정보도_인증_없이_조회된다() throws Exception {
        // given
        when(livePlaybackService.vod(any())).thenReturn(
                new LivePlaybackService.Playback(UUID.randomUUID(), "VOD", "https://vod",
                        UUID.randomUUID(), 3, java.time.Instant.parse("2026-09-10T12:00:00Z")));

        // when & then
        mockMvc.perform(get("/api/v1/lives/{liveId}/vod", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("VOD"))
                .andExpect(jsonPath("$.vodReadyAt").exists());
    }

    @Test
    void 좋아요와_취소는_갱신된_상태를_바디로_돌려준다() throws Exception {
        // given — idempotent라 몇 번을 보내도 결과가 같다. 204 대신 liked/likeCount를 돌려줘
        // FE가 낙관적 업데이트 후 재조회하지 않아도 된다.
        UUID liveId = UUID.randomUUID();
        when(liveLikeService.like(any(), any())).thenReturn(5);
        when(liveLikeService.unlike(any(), any())).thenReturn(4);

        // when & then
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/lives/{liveId}/like", liveId)
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(5));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/lives/{liveId}/like", liveId)
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(4));
    }

    @Test
    void 내_좋아요_여부를_조회한다() throws Exception {
        // given
        UUID liveId = UUID.randomUUID();
        when(liveLikeService.isLiked(any(), any())).thenReturn(true);

        // when & then
        mockMvc.perform(get("/api/v1/lives/{liveId}/like", liveId)
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true));
    }

    @Test
    void VOD_채팅은_경과초를_계산해_내려준다() throws Exception {
        // given — 방송 시작 기준 60초에 온 메시지
        java.time.Instant started = java.time.Instant.parse("2026-09-10T11:00:00Z");
        when(vodChatQueryService.findByRange(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new VodChatQueryService.VodChat(started, List.of(
                        com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity.builder()
                                .senderId(UUID.randomUUID()).content("좋아요")
                                .sentAt(started.plusSeconds(60)).build())));

        // when & then
        mockMvc.perform(get("/api/v1/lives/{liveId}/vod/chat", UUID.randomUUID())
                        .param("fromSec", "0").param("toSec", "120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offsetSec").value(60))
                .andExpect(jsonPath("$[0].content").value("좋아요"));
    }
}
