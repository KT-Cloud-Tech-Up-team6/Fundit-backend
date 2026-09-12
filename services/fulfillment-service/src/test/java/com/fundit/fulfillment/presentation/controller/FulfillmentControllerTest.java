package com.fundit.fulfillment.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.fulfillment.application.tracker.FulfillmentQueryService;
import com.fundit.fulfillment.application.tracker.ScheduleChangeService;
import com.fundit.fulfillment.application.tracker.StageProgressService;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.presentation.GlobalExceptionHandler;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FulfillmentController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class FulfillmentControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FulfillmentQueryService fulfillmentQueryService;
    @MockitoBean
    private StageProgressService stageProgressService;
    @MockitoBean
    private ScheduleChangeService scheduleChangeService;

    @Test
    void 진행현황을_조회하면_200을_반환한다() throws Exception {
        // given
        var view = new FulfillmentQueryService.ProjectFulfillmentView(123L, FulfillmentStage.SHIPPING_OUT,
                Instant.parse("2026-09-08T10:00:00Z"), false,
                List.of(new FulfillmentQueryService.StageSnapshot(FulfillmentStage.SHIPPING_OUT,
                        FulfillmentQueryService.StageProgressStatus.IN_PROGRESS, null, null, "포장 완료", Instant.now())),
                List.of());
        when(fulfillmentQueryService.getProjectFulfillment(123L)).thenReturn(view);

        // when & then
        mockMvc.perform(get("/api/v1/projects/123/fulfillment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(123))
                .andExpect(jsonPath("$.currentStage").value("SHIPPING_OUT"))
                .andExpect(jsonPath("$.isUpdateOverdue").value(false));
    }

    @Test
    void 트래커가_없으면_404를_반환한다() throws Exception {
        // given
        when(fulfillmentQueryService.getProjectFulfillment(999L))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND, "트래커가 없습니다."));

        // when & then
        mockMvc.perform(get("/api/v1/projects/999/fulfillment"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 단계를_전환하면_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        FulfillmentTracker tracker = FulfillmentTracker.create(123L);
        tracker.advanceTo(FulfillmentStage.SHIPPING_OUT);
        when(stageProgressService.transitionStage(123L, sellerId, FulfillmentStage.SHIPPING_OUT)).thenReturn(tracker);

        // when & then
        mockMvc.perform(patch("/api/v1/projects/123/fulfillment/stage")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\": \"SHIPPING_OUT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("SHIPPING_OUT"));
    }

    @Test
    void 인증헤더가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/v1/projects/123/fulfillment/stage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\": \"SHIPPING_OUT\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 화이트리스트_밖_단계값이면_400을_반환한다() throws Exception {
        mockMvc.perform(patch("/api/v1/projects/123/fulfillment/stage")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\": \"NOT_A_STAGE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 상세내용을_등록하면_201대신_200과_등록결과를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        FulfillmentStageDetailJpaEntity saved = FulfillmentStageDetailJpaEntity.builder()
                .id(501L).trackerId(1L).stage("SHIPPING_OUT").detailText("포장 완료, 순차 출고 중")
                .updatedAt(Instant.parse("2026-09-08T10:00:00Z")).build();
        when(stageProgressService.registerStageDetail(any(), any(), any(), any(), any(), any())).thenReturn(saved);

        // when & then
        mockMvc.perform(post("/api/v1/projects/123/fulfillment/stage-details")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage": "SHIPPING_OUT", "detailText": "포장 완료, 순차 출고 중"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stageDetailId").value(501))
                .andExpect(jsonPath("$.detailText").value("포장 완료, 순차 출고 중"));
    }

    @Test
    void 상세내용_누락시_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/projects/123/fulfillment/stage-details")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\": \"SHIPPING_OUT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 일정변경을_등록하면_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        FulfillmentScheduleChangeJpaEntity saved = FulfillmentScheduleChangeJpaEntity.builder()
                .id(88L).trackerId(1L).stage("SHIPPING_OUT").reasonType("STOCK_SHORTAGE")
                .reasonDetail("부자재 입고 지연").oldPlannedDate(Instant.parse("2026-09-05T00:00:00Z"))
                .newPlannedDate(Instant.parse("2026-09-10T00:00:00Z")).changedAt(Instant.parse("2026-09-01T11:00:00Z"))
                .build();
        when(scheduleChangeService.registerScheduleChange(any(), any(), any(), any(), any(), any())).thenReturn(saved);

        // when & then
        mockMvc.perform(post("/api/v1/projects/123/fulfillment/schedule-changes")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage": "SHIPPING_OUT", "reasonType": "STOCK_SHORTAGE",
                                 "reasonDetail": "부자재 입고 지연", "newPlannedDate": "2026-09-10T00:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleChangeId").value(88))
                .andExpect(jsonPath("$.reasonType").value("STOCK_SHORTAGE"));
    }

    @Test
    void 화이트리스트_밖_사유타입이면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/projects/123/fulfillment/schedule-changes")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage": "SHIPPING_OUT", "reasonType": "NOT_A_REASON",
                                 "newPlannedDate": "2026-09-10T00:00:00Z"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
