package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.payment.application.refund.PostShipmentRefundRequestService;
import com.fundit.payment.application.refund.PostShipmentRefundRequestService.PostShipmentRefundRequestResult;
import com.fundit.payment.application.refund.RefundQueryService;
import com.fundit.payment.application.refund.ShippingDelayRefundService;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * cross-service ID 통일(#69) — v2는 내부 해석 호출 없이 fundingId(UUID)를 그대로 받는다/돌려준다.
 */
@WebMvcTest(RefundControllerV2.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class RefundControllerV2Test {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";
    private static final UUID FUNDING_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefundQueryService refundQueryService;
    @MockitoBean
    private PostShipmentRefundRequestService postShipmentRefundRequestService;
    @MockitoBean
    private ShippingDelayRefundService shippingDelayRefundService;

    @Test
    void 목록_조회_응답의_fundingId는_UUID로_채워진다() throws Exception {
        UUID memberId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-09-08T01:00:00Z");
        when(refundQueryService.listMyRefunds(eq(memberId), any(), any(), any())).thenReturn(
                new PageImpl<>(List.of(new RefundQueryService.RefundSummary(3L, FUNDING_ID, "DEFECT", "REQUESTED",
                        89_000L, requestedAt, null, null, null, null)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v2/refunds")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].refundId").value(3))
                .andExpect(jsonPath("$.content[0].fundingId").value(FUNDING_ID.toString()));
    }

    @Test
    void 하자환불을_신청하면_UUID_fundingId를_그대로_전달한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(postShipmentRefundRequestService.request(eq(memberId), eq(FUNDING_ID),
                eq(RefundTriggerType.DEFECT), eq("[DAMAGED] 파손"), any()))
                .thenReturn(new PostShipmentRefundRequestResult(11L, "REQUESTED", 89_000L, 0L, 89_000L));

        mockMvc.perform(post("/api/v2/refunds/defect")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundingId":"%s","defectType":"DAMAGED","reasonDetail":"파손","evidenceUrls":["https://cdn/a.jpg"]}
                                """.formatted(FUNDING_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value(11));
    }

    @Test
    void 발송지연_취소를_신청하면_UUID_fundingId를_그대로_전달한다() throws Exception {
        UUID memberId = UUID.randomUUID();
        when(shippingDelayRefundService.requestCancel(memberId, FUNDING_ID))
                .thenReturn(new ShippingDelayRefundService.ShippingDelayRefundResult(12L, "COMPLETED"));

        mockMvc.perform(post("/api/v2/refunds/shipping-delay")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundingId\":\"%s\"}".formatted(FUNDING_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value(12));
    }

    @Test
    void 목록_조회는_유형과_진행여부_쿼리파라미터를_서비스에_그대로_전달한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        when(refundQueryService.listMyRefunds(eq(memberId), eq(RefundTriggerType.DEFECT), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when & then
        mockMvc.perform(get("/api/v2/refunds")
                        .param("triggerType", "DEFECT")
                        .param("inProgress", "true")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 반품을_신청하면_반품비와_예상환불액을_함께_응답한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        when(postShipmentRefundRequestService.request(eq(memberId), eq(FUNDING_ID),
                eq(RefundTriggerType.RETURN_CHANGE_OF_MIND), eq("[CHANGE_OF_MIND] 색상이 달라요"), any()))
                .thenReturn(new PostShipmentRefundRequestResult(13L, "REQUESTED", 23_000L, 5_000L, 18_000L));

        // when & then
        mockMvc.perform(post("/api/v2/refunds/return")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundingId":"%s","returnReason":"CHANGE_OF_MIND","reasonDetail":"색상이 달라요"}
                                """.formatted(FUNDING_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value(13))
                .andExpect(jsonPath("$.returnShippingFee").value(5000))
                .andExpect(jsonPath("$.estimatedRefundAmount").value(18000));
    }

    @Test
    void 폐기된_단순변심_취소_경로는_더_이상_없다() throws Exception {
        mockMvc.perform(post("/api/v2/refunds/simple-change-of-mind")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fundingId\":\"%s\"}".formatted(FUNDING_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 구매자_귀책_교환을_신청하면_추가_결제_금액과_함께_응답한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        when(postShipmentRefundRequestService.request(eq(memberId), eq(FUNDING_ID),
                eq(RefundTriggerType.EXCHANGE), eq("[WRONG_OPTION] 사이즈 변경"), any()))
                .thenReturn(new PostShipmentRefundRequestResult(14L, "REQUESTED", 89_000L, 0L, 89_000L));

        // when & then
        mockMvc.perform(post("/api/v2/refunds/exchange")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundingId":"%s","exchangeReason":"WRONG_OPTION","reasonDetail":"사이즈 변경","evidenceUrls":["https://cdn/a.jpg"]}
                                """.formatted(FUNDING_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundId").value(14))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.exchangeShippingFee").value(5_000))
                .andExpect(jsonPath("$.additionalPaymentAmount").value(5_000));
    }

    @Test
    void 판매자_귀책_교환이면_추가_결제_금액이_0원이다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        when(postShipmentRefundRequestService.request(eq(memberId), eq(FUNDING_ID),
                eq(RefundTriggerType.EXCHANGE), eq("[WRONG_DELIVERY] 다른 상품이 왔어요"), any()))
                .thenReturn(new PostShipmentRefundRequestResult(15L, "REQUESTED", 89_000L, 0L, 89_000L));

        // when & then
        mockMvc.perform(post("/api/v2/refunds/exchange")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundingId":"%s","exchangeReason":"WRONG_DELIVERY","reasonDetail":"다른 상품이 왔어요"}
                                """.formatted(FUNDING_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.additionalPaymentAmount").value(0));
    }

    /** FE가 사유를 보내도록 전환하는 동안 기존 요청(사유 없음)도 그대로 접수돼야 한다. */
    @Test
    void 교환_사유를_보내지_않으면_기타로_접수된다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        when(postShipmentRefundRequestService.request(eq(memberId), eq(FUNDING_ID),
                eq(RefundTriggerType.EXCHANGE), eq("[OTHER] 사이즈 변경"), any()))
                .thenReturn(new PostShipmentRefundRequestResult(16L, "REQUESTED", 89_000L, 0L, 89_000L));

        // when & then
        mockMvc.perform(post("/api/v2/refunds/exchange")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fundingId":"%s","reasonDetail":"사이즈 변경","evidenceUrls":["https://cdn/a.jpg"]}
                                """.formatted(FUNDING_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.additionalPaymentAmount").value(0));
    }

    @Test
    void 목록_조회_응답은_사유_유형과_상세를_나눠_내려준다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        when(refundQueryService.listMyRefunds(eq(memberId), any(), any(), any())).thenReturn(
                new PageImpl<>(List.of(new RefundQueryService.RefundSummary(4L, FUNDING_ID, "EXCHANGE", "REQUESTED",
                        89_000L, Instant.parse("2026-09-08T01:00:00Z"), "[CHANGE_OF_MIND] 색상이 달라요", null, null,
                        null)), PageRequest.of(0, 20), 1));

        // when & then
        mockMvc.perform(get("/api/v2/refunds")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reasonType").value("CHANGE_OF_MIND"))
                .andExpect(jsonPath("$.content[0].reasonDetail").value("색상이 달라요"))
                .andExpect(jsonPath("$.content[0].additionalPaymentAmount").value(5_000));
    }
}
