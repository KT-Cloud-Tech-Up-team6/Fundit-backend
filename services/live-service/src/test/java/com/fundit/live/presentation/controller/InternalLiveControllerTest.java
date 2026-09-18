package com.fundit.live.presentation.controller;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.live.application.chat.ChatIngestService;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
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
    @MockitoBean private LiveSessionJpaRepository sessionRepository;
    @MockitoBean private LiveChannelJpaRepository channelRepository;

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
    void 내부_키가_없으면_적재할_수_없다() throws Exception {
        // given & when & then — 열려 있으면 임의 채팅 주입이 가능하다(security.md S4)
        mockMvc.perform(post("/internal/v1/lives/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INGEST_BODY.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 방송_상태_조회는_판매자까지_돌려준다() throws Exception {
        // given — order가 라이브 쿠폰 발급 전 "진행 중"을 확인한다
        UUID liveId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(sessionRepository.findByPublicId(liveId)).thenReturn(Optional.of(
                LiveSessionJpaEntity.builder().id(7L).publicId(liveId).channelId(1L)
                        .projectId(UUID.randomUUID()).status(LiveStatus.LIVE).likeCount(0).build()));
        when(channelRepository.findById(1L)).thenReturn(Optional.of(
                LiveChannelJpaEntity.builder().id(1L).sellerId(sellerId).build()));

        // when & then
        mockMvc.perform(get("/internal/v1/lives/{liveId}/status", liveId)
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andExpect(jsonPath("$.sellerId").value(sellerId.toString()));
    }
}
