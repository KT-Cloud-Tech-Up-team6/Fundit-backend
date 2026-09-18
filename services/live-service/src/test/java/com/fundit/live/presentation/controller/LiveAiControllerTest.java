package com.fundit.live.presentation.controller;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.cuesheet.CueSheetService;
import com.fundit.live.application.question.AiAnswerService;
import com.fundit.live.application.question.QuestionInsightService;
import com.fundit.live.infrastructure.persistence.cuesheet.LiveCueSheetJpaEntity;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;
import com.fundit.live.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LiveAiController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class LiveAiControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CueSheetService cueSheetService;
    @MockitoBean private QuestionInsightService questionInsightService;
    @MockitoBean private AiAnswerService aiAnswerService;

    private final UUID userId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    @Test
    void 선택_필드를_생략해도_큐시트_생성_요청이_통한다() throws Exception {
        // given & when & then — 선택 필드가 primitive면 생략이 400이 되어
        // FE가 쓰지 않는 값까지 전부 채워 보내야 한다
        mockMvc.perform(post("/api/v1/lives/{liveId}/cue-sheet", liveId)
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"SCENARIO\",\"targetDurationSec\":580}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("GENERATING"));
    }

    @Test
    void 큐시트_조회는_구간_JSON을_그대로_내려준다() throws Exception {
        // given — 서버가 해석할 이유가 없다
        when(cueSheetService.find(any(), any())).thenReturn(LiveCueSheetJpaEntity.builder()
                .sessionId(1L).mode("SCENARIO").status("COMPLETED").targetDurationSec(580)
                .segments("[{\"order\":1,\"title\":\"오프닝\"}]").build());

        // when & then
        mockMvc.perform(get("/api/v1/lives/{liveId}/cue-sheet", liveId)
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void insights는_aiStatus를_함께_내려준다() throws Exception {
        // given — PREPARING과 "질문 0건"을 화면이 다른 문구로 안내해야 한다(PRD 6.4.4.4)
        when(questionInsightService.insights(any(), any())).thenReturn(
                new QuestionInsightService.Insights("PREPARING", Map.of(), List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/lives/{liveId}/chat/insights", liveId)
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiStatus").value("PREPARING"));
    }

    @Test
    void GENERATE는_채팅에_게시하지_않는다() throws Exception {
        // given — 자동 게시 금지가 정책이다(협의 확정)
        when(aiAnswerService.generate(any(), any(), any(), any()))
                .thenReturn(new AiClient.AnswerDraft("초안입니다", true));

        // when & then
        mockMvc.perform(post("/api/v1/lives/{liveId}/chat/questions/{qid}/ai-answer", liveId, UUID.randomUUID())
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"GENERATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(false))
                .andExpect(jsonPath("$.draftAnswer").value("초안입니다"));

        verify(aiAnswerService, never()).send(any(), any(), any(), anyString());
    }

    @Test
    void SEND해야_실제로_게시된다() throws Exception {
        // given
        when(aiAnswerService.send(any(), any(), any(), anyString())).thenReturn(null);

        // when & then
        mockMvc.perform(post("/api/v1/lives/{liveId}/chat/questions/{qid}/ai-answer", liveId, UUID.randomUUID())
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SEND\",\"finalAnswer\":\"확인했습니다\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true));
    }

    @Test
    void 답변된_질문_모아보기는_질문건수를_반드시_포함한다() throws Exception {
        // given — 시안이 "질문 12건"을 표시한다. 없으면 화면이 안 그려진다(PRD 11.3.4)
        when(questionInsightService.answeredQuestions(any())).thenReturn(List.of(
                LiveQuestionSummaryJpaEntity.builder()
                        .id(1L).publicId(UUID.randomUUID()).sessionId(1L)
                        .summaryText("사이즈가 어떻게 되나요?").relatedQuestionCount(12)
                        .answered(true).answerText("500ml/700ml 두 가지입니다")
                        .answeredAt(Instant.parse("2026-09-10T11:04:00Z")).build()));

        // when & then — 인증 불필요
        mockMvc.perform(get("/api/v1/lives/{liveId}/chat/answered-questions", liveId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].questionCount").value(12))
                .andExpect(jsonPath("$[0].answeredBy").value("SELLER"));
    }
}
