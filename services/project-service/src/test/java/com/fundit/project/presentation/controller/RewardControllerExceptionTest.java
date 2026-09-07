package com.fundit.project.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.project.application.reward.RewardQueryService;
import com.fundit.project.application.reward.RewardService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link RewardControllerTest} 참고. */
@WebMvcTest(RewardController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class RewardControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RewardService rewardService;
    @MockitoBean
    private RewardQueryService rewardQueryService;

    @Test
    void 필수값이_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/rewards")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 수량정합성_위반이면_400을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(rewardService.create(org.mockito.ArgumentMatchers.eq(sellerId), org.mockito.ArgumentMatchers.eq(projectId), any()))
                .thenThrow(new BusinessException(ProjectErrorCode.INVALID_REWARD_QUANTITY));

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/rewards")
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("""
                                {"name":"얼리버드","description":"설명","price":39000,"isLimited":true}
                                """))
                .andExpect(status().isBadRequest());
    }
}
