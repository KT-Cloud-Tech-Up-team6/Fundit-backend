package com.fundit.project.presentation.controller;

import com.fundit.project.application.community.CommunityService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link CommunityControllerTest} 참고. */
@WebMvcTest(CommunityController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class CommunityControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommunityService communityService;

    @Test
    void postType이_QUESTION_CHEER가_아니면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/community/posts")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"postType\":\"WRONG\",\"content\":\"질문\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인증헤더_없이_답변등록시_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/community/posts/1/answer")
                        .contentType("application/json")
                        .content("{\"content\":\"답변\"}"))
                .andExpect(status().isUnauthorized());
    }
}
