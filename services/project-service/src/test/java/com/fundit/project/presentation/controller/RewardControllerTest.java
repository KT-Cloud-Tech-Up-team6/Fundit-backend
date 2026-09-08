package com.fundit.project.presentation.controller;

import com.fundit.project.application.reward.RewardQueryService;
import com.fundit.project.application.reward.RewardService;
import com.fundit.project.domain.reward.Reward;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RewardController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class RewardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RewardService rewardService;
    @MockitoBean
    private RewardQueryService rewardQueryService;

    private Reward reward(Long id) {
        return Reward.create(1L, "얼리버드", "설명", null, 39000L, true, 100, true, null).toBuilder().id(id).build();
    }

    @Test
    void 리워드를_등록하면_201을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(rewardService.create(eq(sellerId), eq(projectId), any())).thenReturn(reward(1L));

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/rewards")
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("""
                                {"name":"얼리버드","description":"설명","price":39000,"isLimited":true,"quantity":100}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rewardId").value(1))
                .andExpect(jsonPath("$.name").value("얼리버드"));
    }

    @Test
    void 리워드를_수정하면_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        when(rewardService.update(eq(sellerId), eq(1L), any())).thenReturn(reward(1L));

        // when & then
        mockMvc.perform(patch("/api/v1/rewards/1")
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("{\"name\":\"새이름\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 리워드를_삭제하면_204를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/v1/rewards/1").header("X-Account-Id", sellerId.toString()))
                .andExpect(status().isNoContent());
        verify(rewardService).delete(sellerId, 1L);
    }

    @Test
    void 고시정보를_등록하면_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        Reward withDisclosure = reward(1L);
        withDisclosure.changeDisclosure("COSMETIC", Map.of("제조국", "대한민국"));
        when(rewardService.updateDisclosure(eq(sellerId), eq(1L), eq("COSMETIC"), any())).thenReturn(withDisclosure);

        // when & then
        mockMvc.perform(put("/api/v1/rewards/1/disclosure")
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("""
                                {"categoryType":"COSMETIC","disclosure":{"제조국":"대한민국"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryType").value("COSMETIC"));
    }

    @Test
    void 환불정책_특이사항을_등록하면_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        Reward withPolicy = reward(1L);
        withPolicy.changeRefundPolicy(true);
        when(rewardService.updateRefundPolicy(sellerId, 1L, true)).thenReturn(withPolicy);

        // when & then
        mockMvc.perform(patch("/api/v1/rewards/1/refund-policy")
                        .header("X-Account-Id", sellerId.toString())
                        .contentType("application/json")
                        .content("{\"simpleRefundDisabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simpleRefundDisabled").value(true));
    }

    @Test
    void 소비자용_리워드_목록을_조회한다() throws Exception {
        // given
        UUID projectId = UUID.randomUUID();
        var view = new RewardQueryService.RewardConsumerView(1L, "R0000001", "얼리버드", 39000L, true, true, 37, List.of(), false);
        when(rewardQueryService.listForConsumer(projectId)).thenReturn(List.of(view));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/rewards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].remainingStock").value(37))
                .andExpect(jsonPath("$[0].soldOut").value(false));
    }

    @Test
    void 리워드_고시정보_목록을_조회한다() throws Exception {
        // given
        UUID projectId = UUID.randomUUID();
        var view = new RewardQueryService.RewardDisclosureView(1L, "얼리버드", "COSMETIC", Map.of("제조국", "대한민국"));
        when(rewardQueryService.listDisclosures(projectId)).thenReturn(List.of(view));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/rewards/disclosures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryType").value("COSMETIC"));
    }
}
