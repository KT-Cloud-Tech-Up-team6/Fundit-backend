package com.fundit.project.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.common.error.BusinessException;
import com.fundit.project.application.reward.RewardQueryService;
import com.fundit.project.application.reward.RewardService;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link RewardControllerTest} 참고. */
@WebMvcTest(RewardController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
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
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void IdempotencyKey가_공백이면_400을_반환한다() throws Exception {
        UUID projectId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/rewards")
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .header("Idempotency-Key", "   ")
                        .contentType("application/json")
                        .content("""
                                {"name":"얼리버드","description":"설명","price":39000,"isLimited":true,"quantity":100}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void IdempotencyKey가_100자를_넘으면_400을_반환한다() throws Exception {
        UUID projectId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/rewards")
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .header("Idempotency-Key", "a".repeat(101))
                        .contentType("application/json")
                        .content("""
                                {"name":"얼리버드","description":"설명","price":39000,"isLimited":true,"quantity":100}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 수량정합성_위반이면_400을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(rewardService.create(org.mockito.ArgumentMatchers.eq(sellerId), org.mockito.ArgumentMatchers.eq(projectId), any(), any(), any()))
                .thenThrow(new BusinessException(ProjectErrorCode.INVALID_REWARD_QUANTITY));

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/rewards")
                        .header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("""
                                {"name":"얼리버드","description":"설명","price":39000,"isLimited":true}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 정률_얼리버드_할인이_100퍼센트면_400을_반환한다() throws Exception {
        // given — 100%면 구매 가격이 0원이 된다(#214). 상한은 99%
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(rewardService.create(org.mockito.ArgumentMatchers.eq(sellerId), org.mockito.ArgumentMatchers.eq(projectId), any(), any(), any()))
                .thenThrow(new BusinessException(ProjectErrorCode.INVALID_EARLY_BIRD_DISCOUNT));

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/rewards")
                        .header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("""
                                {"name":"얼리버드","description":"설명","price":39000,"isLimited":true,"quantity":100,
                                 "isEarlyBird":true,"earlyBirdDiscountType":"RATE","earlyBirdDiscountValue":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EARLY_BIRD_DISCOUNT"));
    }

    @Test
    void 리워드명이_100자를_넘으면_400을_반환한다() throws Exception {
        // given & when & then — DB 컬럼 길이(100)를 넘으면 500이 나던 것을 400으로 막는다(FE BE-11)
        mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/rewards")
                        .header("X-User-Id", UUID.randomUUID().toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","description":"설명","price":39000,"isLimited":true,"quantity":100}
                                """.formatted("가".repeat(101))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 옵션_그룹명이나_옵션값이_50자를_넘으면_400을_반환한다() throws Exception {
        // given & when & then
        String longText = "가".repeat(51);
        for (String options : new String[]{
                "[{\"groupName\":\"%s\",\"values\":[\"화이트\"]}]".formatted(longText),
                "[{\"groupName\":\"색상\",\"values\":[\"%s\"]}]".formatted(longText)}) {
            mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/rewards")
                            .header("X-User-Id", UUID.randomUUID().toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                            .contentType("application/json")
                            .content("""
                                    {"name":"얼리버드","description":"설명","price":39000,"isLimited":true,"quantity":100,"options":%s}
                                    """.formatted(options)))
                    .andExpect(status().isBadRequest());
        }
    }
}
