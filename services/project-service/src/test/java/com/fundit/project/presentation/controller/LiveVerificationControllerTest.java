package com.fundit.project.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.project.application.liveverification.LiveQuestionAnswerView;
import com.fundit.project.application.liveverification.LiveVerificationService;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaEntity;
import com.fundit.project.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LiveVerificationController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class LiveVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LiveVerificationService liveVerificationService;

    private LiveVerificationJpaEntity entity() {
        return LiveVerificationJpaEntity.builder()
                .id(301L).projectId(1L).questionSummaryId("live-q-1").answer("답변").questionCount(12)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void 등록하면_201을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(liveVerificationService.create(eq(sellerId), eq(projectId), eq("live-q-1"), eq("답변"))).thenReturn(entity());

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/live-verifications")
                        .header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("{\"questionSummaryId\":\"live-q-1\",\"answer\":\"답변\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.answer").value("답변"));
    }

    @Test
    void 수정하면_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        when(liveVerificationService.update(eq(sellerId), eq(301L), eq("수정답변"))).thenReturn(entity());

        // when & then
        mockMvc.perform(patch("/api/v1/live-verifications/301")
                        .header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("{\"answer\":\"수정답변\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 삭제하면_204를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/v1/live-verifications/301").header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isNoContent());
        verify(liveVerificationService).delete(sellerId, 301L);
    }

    @Test
    void 소비자_목록조회는_질문문구와_건수를_함께_반환한다() throws Exception {
        // given
        UUID projectId = UUID.randomUUID();
        when(liveVerificationService.listForConsumer(projectId)).thenReturn(List.of(
                new LiveQuestionAnswerView("live-q-1", "배송은 얼마나 걸리나요?", 12, 301L, "답변")));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/live-verifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].questionSummaryId").value("live-q-1"))
                .andExpect(jsonPath("$.content[0].questionText").value("배송은 얼마나 걸리나요?"))
                .andExpect(jsonPath("$.content[0].questionCount").value(12));
    }

    @Test
    void 판매자_질문목록조회는_미답변_질문도_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(liveVerificationService.listQuestionsForSeller(sellerId, projectId)).thenReturn(List.of(
                new LiveQuestionAnswerView("live-q-1", "배송은 얼마나 걸리나요?", 12, 301L, "답변"),
                new LiveQuestionAnswerView("live-q-2", "방수 되나요?", 5, null, null)));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/live-questions")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].answered").value(true))
                .andExpect(jsonPath("$.content[1].answered").value(false))
                .andExpect(jsonPath("$.content[1].questionText").value("방수 되나요?"));
    }
}
