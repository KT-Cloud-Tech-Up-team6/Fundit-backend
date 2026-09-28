package com.fundit.live.presentation.controller;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.live.application.chat.ChatIngestService;
import com.fundit.live.application.highlight.HighlightService;
import com.fundit.live.application.session.LiveStatusQueryService;
import com.fundit.live.infrastructure.security.InternalEndpointConfig;
import com.fundit.live.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalLiveController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class, InternalEndpointConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class InternalLiveControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ChatIngestService chatIngestService;
    @MockitoBean private HighlightService highlightService;
    @MockitoBean private LiveStatusQueryService liveStatusQueryService;

    private static final String INGEST_BODY = """
            { "roomArn": "arn:room", "ivsMessageId": "msg-1",
              "senderId": "%s", "content": "안녕하세요",
              "sentAt": "2026-09-10T11:00:00Z" }
            """;

    @Test
    void 채팅_적재는_204다() throws Exception {
        // given
        when(chatIngestService.ingest(anyString(), anyString(), any(), anyString(), any())).thenReturn(true);

        // when & then
        mockMvc.perform(post("/internal/v1/lives/chat/messages")
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INGEST_BODY.formatted(UUID.randomUUID())))
                .andExpect(status().isNoContent());
    }

    @Test
    void 재전송_중복도_204다() throws Exception {
        // given — 중복은 정상 흐름이라 에러로 올리지 않는다
        when(chatIngestService.ingest(anyString(), anyString(), any(), anyString(), any())).thenReturn(false);

        // when & then
        mockMvc.perform(post("/internal/v1/lives/chat/messages")
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INGEST_BODY.formatted(UUID.randomUUID())))
                .andExpect(status().isNoContent());
    }


    @Test
    void 방송_상태_조회는_판매자까지_돌려준다() throws Exception {
        // given — order가 라이브 쿠폰 발급 전 "진행 중"을 확인한다
        UUID liveId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(liveStatusQueryService.find(liveId)).thenReturn(
                new LiveStatusQueryService.LiveStatus(liveId, 7L, "LIVE", sellerId));

        // when & then
        mockMvc.perform(get("/internal/v1/lives/{liveId}/status", liveId)
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.sellerId").value(sellerId.toString()));
    }

    @Test
    void 프로젝트_진행중_방송은_세션_id까지_돌려준다() throws Exception {
        // given
        UUID projectId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();
        when(liveStatusQueryService.findActiveByProject(projectId)).thenReturn(Optional.of(
                new LiveStatusQueryService.LiveStatus(liveId, 42L, "LIVE", UUID.randomUUID())));

        // when & then
        mockMvc.perform(get("/internal/v1/lives/by-project/{projectId}/active-status", projectId)
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(42))
                .andExpect(jsonPath("$.liveId").value(liveId.toString()));
    }

    @Test
    void 프로젝트가_방송_중이_아니면_200에_전부_null이다() throws Exception {
        // given — 404로 주면 order가 "호출 실패"와 구분하지 못한다
        UUID projectId = UUID.randomUUID();
        when(liveStatusQueryService.findActiveByProject(projectId)).thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/internal/v1/lives/by-project/{projectId}/active-status", projectId)
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveId").value(nullValue()))
                .andExpect(jsonPath("$.sessionId").value(nullValue()))
                .andExpect(jsonPath("$.status").value(nullValue()))
                .andExpect(jsonPath("$.sellerId").value(nullValue()));
    }

    @Test
    void 세션_상태_조회는_종료된_방송도_실제_상태를_돌려준다() throws Exception {
        // given
        when(liveStatusQueryService.findBySessionId(42L)).thenReturn(Optional.of(
                new LiveStatusQueryService.LiveStatus(UUID.randomUUID(), 42L, "ENDED", UUID.randomUUID())));

        // when & then
        mockMvc.perform(get("/internal/v1/lives/sessions/{sessionId}/status", 42L)
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));
    }

    @Test
    void 세션이_없으면_200에_전부_null이다() throws Exception {
        // given
        when(liveStatusQueryService.findBySessionId(99L)).thenReturn(Optional.empty());

        // when & then
        mockMvc.perform(get("/internal/v1/lives/sessions/{sessionId}/status", 99L)
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(nullValue()))
                .andExpect(jsonPath("$.sessionId").value(nullValue()));
    }
}
