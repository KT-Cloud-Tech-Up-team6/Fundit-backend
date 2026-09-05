package com.fundit.project.presentation.controller;

import com.fundit.project.application.liveverification.LiveVerificationService;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaEntity;
import com.fundit.project.infrastructure.security.CurrentAdminArgumentResolver;
import com.fundit.project.infrastructure.security.CurrentMemberArgumentResolver;
import com.fundit.project.infrastructure.security.WebConfig;
import com.fundit.project.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
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
                        .header("X-Account-Id", sellerId.toString())
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
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("{\"answer\":\"수정답변\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 삭제하면_204를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/v1/live-verifications/301").header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isNoContent());
        verify(liveVerificationService).delete(sellerId, 301L);
    }

    @Test
    void 소비자_목록조회는_content_배열로_반환한다() throws Exception {
        // given
        UUID projectId = UUID.randomUUID();
        when(liveVerificationService.listForConsumer(projectId)).thenReturn(List.of(entity()));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/live-verifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].questionCount").value(12));
    }
}
