package com.fundit.project.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.project.application.reward.RewardQueryService;
import com.fundit.project.application.reward.RewardService;
import com.fundit.project.domain.reward.EarlyBirdDiscountType;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RewardController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class RewardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RewardService rewardService;
    @MockitoBean
    private RewardQueryService rewardQueryService;

    private Reward reward(Long id) {
        return Reward.create(1L, "얼리버드", "설명", null, 39000L, true, 100, true,
                EarlyBirdDiscountType.RATE, 10L, null).toBuilder().id(id).build();
    }

    @Test
    void 리워드를_등록하면_201을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(rewardService.create(eq(sellerId), eq(projectId), any())).thenReturn(reward(1L));

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/rewards")
                        .header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("""
                                {"name":"얼리버드","description":"설명","price":39000,"isLimited":true,"quantity":100}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rewardId").value(1))
                .andExpect(jsonPath("$.name").value("얼리버드"));
    }

    @Test
    void quantity가_음수1이면_무제한으로_정규화해서_전달한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(rewardService.create(eq(sellerId), eq(projectId), any())).thenReturn(reward(1L));

        // when
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/rewards")
                        .header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("""
                                {"name":"무제한리워드","description":"설명","price":10000,"isLimited":false,"quantity":-1}
                                """))
                .andExpect(status().isCreated());

        // then
        ArgumentCaptor<RewardService.CreateRewardCommand> captor = ArgumentCaptor.forClass(RewardService.CreateRewardCommand.class);
        verify(rewardService).create(eq(sellerId), eq(projectId), captor.capture());
        assertThat(captor.getValue().isLimited()).isFalse();
        assertThat(captor.getValue().quantity()).isNull();
    }

    @Test
    void isLimited가_true이면서_quantity가_음수1이면_정규화하지_않고_그대로_전달한다() throws Exception {
        // given — 모순 조합, 도메인 검증(quantity>=0)이 거부하도록 정규화하지 않고 그대로 흘려보낸다
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(rewardService.create(eq(sellerId), eq(projectId), any())).thenReturn(reward(1L));

        // when
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/rewards")
                        .header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("""
                                {"name":"모순리워드","description":"설명","price":10000,"isLimited":true,"quantity":-1}
                                """))
                .andExpect(status().isCreated());

        // then
        ArgumentCaptor<RewardService.CreateRewardCommand> captor = ArgumentCaptor.forClass(RewardService.CreateRewardCommand.class);
        verify(rewardService).create(eq(sellerId), eq(projectId), captor.capture());
        assertThat(captor.getValue().isLimited()).isTrue();
        assertThat(captor.getValue().quantity()).isEqualTo(-1);
    }

    @Test
    void 수정_요청에서_isLimited_없이_quantity만_음수1이면_무제한으로_정규화한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        when(rewardService.update(eq(sellerId), eq(1L), any())).thenReturn(reward(1L));

        // when
        mockMvc.perform(patch("/api/v1/rewards/1")
                        .header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("{\"quantity\":-1}"))
                .andExpect(status().isOk());

        // then
        ArgumentCaptor<RewardService.UpdateRewardCommand> captor = ArgumentCaptor.forClass(RewardService.UpdateRewardCommand.class);
        verify(rewardService).update(eq(sellerId), eq(1L), captor.capture());
        assertThat(captor.getValue().isLimited()).isFalse();
        assertThat(captor.getValue().quantity()).isNull();
    }

    @Test
    void 리워드를_수정하면_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        when(rewardService.update(eq(sellerId), eq(1L), any())).thenReturn(reward(1L));

        // when & then
        mockMvc.perform(patch("/api/v1/rewards/1")
                        .header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("{\"name\":\"새이름\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 리워드를_삭제하면_204를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/v1/rewards/1").header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isNoContent());
        verify(rewardService).delete(sellerId, 1L);
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
                        .header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("{\"simpleRefundDisabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simpleRefundDisabled").value(true));
    }

    @Test
    void 소비자용_리워드_목록을_조회한다() throws Exception {
        // given
        UUID projectId = UUID.randomUUID();
        var view = new RewardQueryService.RewardConsumerView(1L, "R0000001", "얼리버드", "설명", "https://example.com/image.png",
                39000L, true, EarlyBirdDiscountType.RATE, 10L, 35100L, true, 37, List.of(), false);
        when(rewardQueryService.listForConsumer(projectId)).thenReturn(List.of(view));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/rewards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].remainingStock").value(37))
                .andExpect(jsonPath("$[0].soldOut").value(false));
    }

    @Test
    void 소비자용_리워드_상세를_조회한다() throws Exception {
        // given
        var view = new RewardQueryService.RewardConsumerView(1L, "R0000001", "얼리버드", "설명", "https://example.com/image.png",
                39000L, true, EarlyBirdDiscountType.RATE, 10L, 35100L, true, 37, List.of(), false);
        when(rewardQueryService.getForConsumer(1L)).thenReturn(view);

        // when & then
        mockMvc.perform(get("/api/v1/rewards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("설명"))
                .andExpect(jsonPath("$.imageUrl").value("https://example.com/image.png"));
    }

    @Test
    void 판매자용_리워드_목록을_조회한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(rewardQueryService.listForSeller(sellerId, projectId)).thenReturn(List.of(reward(1L)));

        // when & then
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/rewards/mine")
                        .header("X-User-Id", sellerId.toString()).header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rewardId").value(1))
                .andExpect(jsonPath("$[0].earlyBirdDiscountType").value("RATE"));
    }
}
