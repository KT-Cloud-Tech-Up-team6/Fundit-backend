package com.fundit.project.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.project.ProjectQueryService;
import com.fundit.project.application.project.ProjectService;
import com.fundit.project.application.project.ProjectStatsService;
import com.fundit.project.domain.ProjectErrorCode;
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

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link ProjectControllerTest} 참고. */
@WebMvcTest(ProjectController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class ProjectControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;
    @MockitoBean
    private ProjectQueryService projectQueryService;
    @MockitoBean
    private ProjectStatsService projectStatsService;

    @Test
    void 인증헤더_없이_목록조회하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void status값이_올바르지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void page가_음수이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void DRAFT가_아닌_프로젝트_삭제시도는_422를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new BusinessException(ProjectErrorCode.PROJECT_NOT_DELETABLE))
                .when(projectService).delete(sellerId, publicId);

        // when & then
        mockMvc.perform(delete("/api/v1/projects/" + publicId).header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 목표금액이_음수이면_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/v1/projects/" + UUID.randomUUID() + "/basic-info")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"goalAmount\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agreed값이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/privacy-consent")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 필수항목_미완료_상태로_제출하면_422를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(projectService.submit(sellerId, publicId))
                .thenThrow(new BusinessException(ProjectErrorCode.PROJECT_NOT_SUBMITTABLE));

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + publicId + "/submit").header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void 비공개_프로젝트_공개상세조회는_404를_반환한다() throws Exception {
        // given
        UUID publicId = UUID.randomUUID();
        when(projectQueryService.getPublicDetail(publicId)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + publicId))
                .andExpect(status().isNotFound());
    }

    @Test
    void 타인_소유_프로젝트_미리보기는_403을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        when(projectQueryService.getPreview(sellerId, publicId)).thenThrow(new BusinessException(CommonErrorCode.FORBIDDEN));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + publicId + "/preview").header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isForbidden());
    }
}
