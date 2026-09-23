package com.fundit.order.presentation.controller;

import com.fundit.order.application.live.LiveStatusClient;
import com.fundit.order.application.order.LiveOrderStatsService;
import com.fundit.order.application.order.OrderCancelService;
import com.fundit.order.application.order.OrderCreateService;
import com.fundit.order.application.order.OrderPreviewService;
import com.fundit.order.application.order.OrderPricingService;
import com.fundit.order.application.order.OrderQueryService;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.IssuerType;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingLineItem;
import com.fundit.order.domain.funding.FundingLineItemOption;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.funding.ShippingAddress;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.order.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.infrastructure.persistence.funding.query.LiveOrderStatsProjection;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class OrderControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";
    private static final UUID PROJECT_ID = UUID.randomUUID();

    private final String requestBody = """
            {
              "projectId": "%s",
              "lineItems": [ { "rewardId": 1, "quantity": 1, "optionValueIds": [] } ],
              "shippingAddress": { "recipientName": "홍길동", "phoneNumber": "010-1234-5678",
                "zipcode": "12345", "addressLine1": "서울시", "addressLine2": "101동" }
            }
            """.formatted(PROJECT_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderPreviewService orderPreviewService;
    @MockitoBean
    private OrderCreateService orderCreateService;
    @MockitoBean
    private OrderQueryService orderQueryService;
    @MockitoBean
    private OrderCancelService orderCancelService;
    @MockitoBean
    private LiveOrderStatsService liveOrderStatsService;
    @MockitoBean
    private LiveStatusClient liveStatusClient;

    private Funding funding(UUID memberId, UUID publicId, FundingStatus status) {
        return Funding.builder().id(1L).publicId(publicId).memberId(memberId).projectId(PROJECT_ID).projectTitle("프로젝트")
                .status(status).shippingAddress(new ShippingAddress("홍길동", "010", "12345", "주소", null))
                .shippingFee(3_000L).paymentExpiresAt(Instant.now().plusSeconds(1800))
                .lineItems(List.of()).createdAt(Instant.now()).build();
    }

    @Test
    void 미리보기_요청하면_금액이_계산되어_200을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        var pricing = new OrderPricingService.PricingResult(10_000L, 3_000L, 0L, 13_000L, List.of(), List.of(), List.of());
        when(orderPreviewService.preview(eq(memberId), eq(PROJECT_ID), any(), any(), eq(false))).thenReturn(pricing);

        // when & then
        mockMvc.perform(post("/api/v1/orders/preview")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalAmount").value(13_000));
    }

    @Test
    void 미리보기_응답에_적용쿠폰과_미적용쿠폰이_포함된다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        var applied = new OrderPricingService.AppliedCoupon(1L, "WELCOME10", IssuerType.PLATFORM, DiscountType.RATE, 1_000L);
        var unavailable = new OrderPricingService.UnavailableCoupon("EXPIRED10", "EXPIRED");
        var pricing = new OrderPricingService.PricingResult(10_000L, 3_000L, 1_000L, 12_000L,
                List.of(), List.of(applied), List.of(unavailable));
        when(orderPreviewService.preview(eq(memberId), eq(PROJECT_ID), any(), any(), eq(false))).thenReturn(pricing);

        // when & then
        mockMvc.perform(post("/api/v1/orders/preview")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCoupons[0].couponCode").value("WELCOME10"))
                .andExpect(jsonPath("$.appliedCoupons[0].issuerType").value("PLATFORM"))
                .andExpect(jsonPath("$.unavailableCoupons[0].couponCode").value("EXPIRED10"))
                .andExpect(jsonPath("$.unavailableCoupons[0].reason").value("EXPIRED"));
    }

    @Test
    void autoApplyBestCoupon이_true면_couponCodes_대신_자동추천_플래그를_전달한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        var applied = new OrderPricingService.AppliedCoupon(2L, "BEST10", IssuerType.PLATFORM, DiscountType.RATE, 1_500L);
        var pricing = new OrderPricingService.PricingResult(10_000L, 3_000L, 1_500L, 11_500L,
                List.of(), List.of(applied), List.of());
        when(orderPreviewService.preview(eq(memberId), eq(PROJECT_ID), any(), any(), eq(true))).thenReturn(pricing);

        // when & then
        mockMvc.perform(post("/api/v1/orders/preview")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content("""
                                {"projectId": "%s", "autoApplyBestCoupon": true,
                                 "lineItems": [ { "rewardId": 1, "quantity": 1, "optionValueIds": [] } ],
                                 "shippingAddress": { "recipientName": "홍길동", "phoneNumber": "010-1234-5678",
                                   "zipcode": "12345", "addressLine1": "서울시", "addressLine2": "101동" }
                                }
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedCoupons[0].couponCode").value("BEST10"));
    }

    @Test
    void 인증헤더가_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/orders/preview")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 필수값이_비어있으면_400을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();

        // when & then — lineItems가 빈 배열
        mockMvc.perform(post("/api/v1/orders/preview")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content("""
                                {"projectId": "%s", "lineItems": [],
                                 "shippingAddress": {"recipientName":"홍길동","phoneNumber":"010","zipcode":"12345","addressLine1":"주소"}}
                                """.formatted(PROJECT_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 주문을_생성하면_201을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Funding funding = funding(memberId, orderId, FundingStatus.PENDING);
        when(orderCreateService.create(eq(memberId), eq(PROJECT_ID), any(), any(), any(), eq(false), any(), any(), any()))
                .thenReturn(new OrderCreateService.OrderCreateResult(funding, 13_000L, false));

        // when & then
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void Idempotency_Key로_재요청하면_200과_기존_주문을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Funding funding = funding(memberId, orderId, FundingStatus.PENDING);
        when(orderCreateService.create(eq(memberId), eq(PROJECT_ID), any(), any(), any(), eq(false),
                eq("retry-key-1"), any(), any()))
                .thenReturn(new OrderCreateService.OrderCreateResult(funding, 13_000L, true));

        // when & then
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .header("Idempotency-Key", "retry-key-1")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()));
    }

    @Test
    void 내_참여_목록을_조회하면_projectId가_UUID로_채워진다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        Funding funding = funding(memberId, UUID.randomUUID(), FundingStatus.PENDING);
        when(orderQueryService.listMyOrders(eq(memberId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(new OrderQueryService.OrderListItem(funding, null, 0L, List.of("CANCEL")))));

        // when & then
        mockMvc.perform(get("/api/v1/orders").header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].projectId").value(PROJECT_ID.toString()));
    }

    @Test
    void 참여_상세를_조회한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Funding funding = funding(memberId, orderId, FundingStatus.PENDING);
        when(orderQueryService.getDetail(memberId, orderId))
                .thenReturn(new OrderQueryService.FundingDetail(funding, 0L, List.of("CANCEL"), null));

        // when & then
        mockMvc.perform(get("/api/v1/orders/" + orderId).header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.projectTitle").value("프로젝트"))
                .andExpect(jsonPath("$.paymentExpiresAt").value(funding.getPaymentExpiresAt().toString()));
    }

    @Test
    void 참여_상세에_라인아이템_옵션이_포함된다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        FundingLineItemOption option = new FundingLineItemOption(1L, 10L, "색상", 100L, "블랙");
        FundingLineItem lineItem = new FundingLineItem(1L, 1L, "얼리버드 패키지", 2, 10_000L, List.of(option));
        Funding funding = funding(memberId, orderId, FundingStatus.PENDING).toBuilder()
                .lineItems(List.of(lineItem)).build();
        var projectSummary = new com.fundit.order.application.catalog.ProjectSummaryClient.ProjectSummary(
                "프로젝트", "https://cdn/x.png", "메이커");
        when(orderQueryService.getDetail(memberId, orderId))
                .thenReturn(new OrderQueryService.FundingDetail(funding, 0L, List.of("CANCEL"), projectSummary));

        // when & then
        mockMvc.perform(get("/api/v1/orders/" + orderId).header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thumbnailUrl").value("https://cdn/x.png"))
                .andExpect(jsonPath("$.lineItems[0].rewardName").value("얼리버드 패키지"))
                .andExpect(jsonPath("$.lineItems[0].options[0].optionValueId").value(100))
                .andExpect(jsonPath("$.lineItems[0].options[0].optionGroupName").value("색상"))
                .andExpect(jsonPath("$.lineItems[0].options[0].optionValue").value("블랙"));
    }

    @Test
    void 참여를_취소한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderCancelService.cancel(memberId, orderId))
                .thenReturn(funding(memberId, orderId, FundingStatus.CANCELLED_BY_MEMBER));

        // when & then
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_MEMBER"));
    }

    @Test
    void 방송중이면_주문에_세션ID를_실어_넘긴다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        Funding funding = funding(memberId, UUID.randomUUID(), FundingStatus.PENDING);
        when(liveStatusClient.findActiveByProject(PROJECT_ID)).thenReturn(
                Optional.of(new LiveStatusClient.LiveStatus(UUID.randomUUID(), 42L, "LIVE", UUID.randomUUID())));
        when(orderCreateService.create(eq(memberId), eq(PROJECT_ID), any(), any(), any(), eq(false),
                any(), any(), eq(42L)))
                .thenReturn(new OrderCreateService.OrderCreateResult(funding, 13_000L, false));

        // when & then
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void live_조회에_실패해도_주문은_생성된다() throws Exception {
        // given — 라이브ID는 꼬리표라 못 달아도 본 거래를 막지 않는다(확정 계약 5번)
        UUID memberId = UUID.randomUUID();
        Funding funding = funding(memberId, UUID.randomUUID(), FundingStatus.PENDING);
        when(liveStatusClient.findActiveByProject(PROJECT_ID))
                .thenThrow(new DependencyFailureException(new IllegalStateException("timeout")));
        when(orderCreateService.create(eq(memberId), eq(PROJECT_ID), any(), any(), any(), eq(false),
                any(), any(), isNull()))
                .thenReturn(new OrderCreateService.OrderCreateResult(funding, 13_000L, false));

        // when & then
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated());
    }

    @Test
    void 방송_집계를_조회하면_건수와_금액을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();
        when(liveOrderStatsService.getStats(memberId, liveId)).thenReturn(new LiveOrderStatsProjection() {
            public long getPaidCount() {
                return 12;
            }

            public long getPaidAmount() {
                return 480_000;
            }

            public long getPendingCount() {
                return 3;
            }

            public long getPendingAmount() {
                return 90_000;
            }
        });

        // when & then
        mockMvc.perform(get("/api/v1/orders/live-stats")
                        .param("liveId", liveId.toString())
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liveId").value(liveId.toString()))
                .andExpect(jsonPath("$.paidCount").value(12))
                .andExpect(jsonPath("$.paidAmount").value(480000))
                .andExpect(jsonPath("$.pendingCount").value(3))
                .andExpect(jsonPath("$.pendingAmount").value(90000));
    }
}
