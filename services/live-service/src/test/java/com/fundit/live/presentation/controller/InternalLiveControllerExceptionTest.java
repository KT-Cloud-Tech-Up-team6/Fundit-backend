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
class InternalLiveControllerExceptionTest {

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
    void 내부_키가_없으면_적재할_수_없다() throws Exception {
        // given & when & then — 열려 있으면 임의 채팅 주입이 가능하다(security.md S4)
        mockMvc.perform(post("/internal/v1/lives/chat/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INGEST_BODY.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 필수값이_빠진_하이라이트_콜백은_400이다() throws Exception {
        // given & when & then — 검증이 없으면 NOT NULL 제약 위반으로 500이 나고,
        // AI가 잘못 보낸 건데 우리 서버 오류로 보인다
        mockMvc.perform(post("/internal/v1/lives/{liveId}/highlights", UUID.randomUUID())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"sceneLabel\":\"DEMO\",\"startSec\":10,\"status\":\"COMPLETED\"}]"))
                .andExpect(status().isBadRequest());
    }
}
