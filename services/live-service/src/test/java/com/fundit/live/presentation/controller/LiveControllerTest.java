package com.fundit.live.presentation.controller;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.live.application.chat.ChatTokenService;
import com.fundit.live.application.session.LiveCreateService;
import com.fundit.live.application.session.LivePlaybackService;
import com.fundit.live.application.session.LiveQueryService;
import com.fundit.live.application.session.LiveSettingsService;
import com.fundit.live.application.session.LiveStreamService;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.presentation.GlobalExceptionHandler;
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
    void projectId가_없으면_400이다() throws Exception {
        // given & when & then — 입력 검증은 서버에서 한다(security.md S2)
        mockMvc.perform(post("/api/v1/lives")
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 소비자_목록은_인증_없이_조회된다() throws Exception {
        // given — 방송 자체가 공개다
        when(liveQueryService.findPublic(any(), any())).thenReturn(new PageImpl<>(List.of()));

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
        when(liveQueryService.findLiveBanner()).thenReturn(List.of(entity));

        // when & then
        mockMvc.perform(get("/api/v1/lives/banner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].introText").value("5분만에 알아보는 신제품"))
                .andExpect(jsonPath("$[0].title").doesNotExist());
    }

    @Test
    void 내_목록은_로그인_사용자_기준으로_조회한다() throws Exception {
        // given
        when(liveQueryService.findMine(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/lives/mine")
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk());
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
    void 소개문구가_200자를_넘으면_400이다() throws Exception {
        // given & when & then — 입력 검증은 서버에서 한다(security.md S2)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/lives/{liveId}/settings", UUID.randomUUID())
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"introText\":\"%s\"}".formatted("가".repeat(201))))
                .andExpect(status().isBadRequest());
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
}
