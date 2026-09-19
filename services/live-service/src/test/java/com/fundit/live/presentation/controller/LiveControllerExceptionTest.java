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
class LiveControllerExceptionTest {

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
}
