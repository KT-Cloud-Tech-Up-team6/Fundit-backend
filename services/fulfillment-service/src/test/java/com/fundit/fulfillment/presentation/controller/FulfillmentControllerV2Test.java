package com.fundit.fulfillment.presentation.controller;

import com.fundit.fulfillment.application.tracker.FulfillmentQueryService;
import com.fundit.fulfillment.application.tracker.ScheduleChangeService;
import com.fundit.fulfillment.application.tracker.StageProgressService;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.common.webmvc.auth.CommonWebConfig;
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

/**
 * cross-service ID 통일(#69) — v2는 내부 해석 호출 없이 projectId(UUID)를 그대로 받는다.
 */
@WebMvcTest(FulfillmentControllerV2.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class FulfillmentControllerV2Test {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";
    private static final UUID PROJECT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FulfillmentQueryService fulfillmentQueryService;
    @MockitoBean
    private StageProgressService stageProgressService;
    @MockitoBean
    private ScheduleChangeService scheduleChangeService;

    @Test
    void 진행현황을_조회하면_UUID_projectId를_반환한다() throws Exception {
        // given
        var view = new FulfillmentQueryService.ProjectFulfillmentView(PROJECT_ID, FulfillmentStage.SHIPPING_OUT,
                Instant.parse("2026-09-08T10:00:00Z"), false,
                List.of(new FulfillmentQueryService.StageSnapshot(FulfillmentStage.SHIPPING_OUT,
                        FulfillmentQueryService.StageProgressStatus.IN_PROGRESS, null, null, "포장 완료", Instant.now())),
                List.of());
        when(fulfillmentQueryService.getProjectFulfillment(PROJECT_ID)).thenReturn(view);

        // when & then
        mockMvc.perform(get("/api/v2/projects/{projectId}/fulfillment", PROJECT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.currentStage").value("SHIPPING_OUT"));
    }

    @Test
    void 단계를_전환하면_UUID_projectId를_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        FulfillmentTracker tracker = FulfillmentTracker.create(PROJECT_ID);
        tracker.advanceTo(FulfillmentStage.SHIPPING_OUT);
        when(stageProgressService.transitionStage(PROJECT_ID, sellerId, FulfillmentStage.SHIPPING_OUT)).thenReturn(tracker);

        // when & then
        mockMvc.perform(patch("/api/v2/projects/{projectId}/fulfillment/stage", PROJECT_ID)
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\": \"SHIPPING_OUT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(PROJECT_ID.toString()))
                .andExpect(jsonPath("$.currentStage").value("SHIPPING_OUT"));
    }

    @Test
    void 상세내용을_등록하면_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        FulfillmentStageDetailJpaEntity saved = FulfillmentStageDetailJpaEntity.builder()
                .id(501L).trackerId(1L).stage("SHIPPING_OUT").detailText("포장 완료, 순차 출고 중")
                .updatedAt(Instant.parse("2026-09-08T10:00:00Z")).build();
        when(stageProgressService.registerStageDetail(any(), any(), any(), any(), any(), any())).thenReturn(saved);

        // when & then
        mockMvc.perform(post("/api/v2/projects/{projectId}/fulfillment/stage-details", PROJECT_ID)
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage": "SHIPPING_OUT", "detailText": "포장 완료, 순차 출고 중"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stageDetailId").value(501));
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
        mockMvc.perform(post("/api/v2/projects/{projectId}/fulfillment/schedule-changes", PROJECT_ID)
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage": "SHIPPING_OUT", "reasonType": "STOCK_SHORTAGE",
                                 "reasonDetail": "부자재 입고 지연", "newPlannedDate": "2026-09-10T00:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleChangeId").value(88));
    }
}
