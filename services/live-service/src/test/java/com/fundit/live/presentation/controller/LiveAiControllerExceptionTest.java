package com.fundit.live.presentation.controller;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.live.application.cuesheet.CueSheetService;
import com.fundit.live.application.question.AiAnswerService;
import com.fundit.live.application.question.QuestionInsightService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LiveAiController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class LiveAiControllerExceptionTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CueSheetService cueSheetService;
    @MockitoBean private QuestionInsightService questionInsightService;
    @MockitoBean private AiAnswerService aiAnswerService;

    private final UUID userId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    @Test
    void 큐시트_판매자_답변이_1000자를_넘으면_400이다() throws Exception {
        // given & when & then — AI 요청 본문이 무한정 커지지 않게 서버에서 막는다(S2)
        mockMvc.perform(post("/api/v1/lives/{liveId}/cue-sheet", liveId)
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"SCENARIO\",\"targetDurationSec\":580,\"motivation\":\"%s\"}"
                                .formatted("가".repeat(1001))))
                .andExpect(status().isBadRequest());
    }
}
