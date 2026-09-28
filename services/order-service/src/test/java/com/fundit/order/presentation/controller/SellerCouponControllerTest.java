package com.fundit.order.presentation.controller;

import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.application.coupon.MakerCouponIssueService;
import com.fundit.order.application.live.LiveStatusClient;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponTargetScope;
import com.fundit.order.domain.coupon.DiscountType;
import com.fundit.order.domain.coupon.IssueChannel;
import com.fundit.order.domain.coupon.IssuerType;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.order.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerCouponController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class SellerCouponControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MakerCouponIssueService makerCouponIssueService;
    @MockitoBean
    private LiveStatusClient liveStatusClient;
    @MockitoBean
    private ProjectOwnershipClient projectOwnershipClient;

    @Test
    void 메이커_쿠폰을_발급하면_201을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        when(projectOwnershipClient.findPublicId(123L)).thenReturn(Optional.of(UUID.randomUUID()));
        Coupon coupon = Coupon.builder().id(1L).couponCode("PJT123-A1B2").couponName("오픈 기념 할인")
                .discountType(DiscountType.RATE).discountValue(10).maxDiscountAmount(5_000L)
                .budgetLimit(1_000_000L).usedBudgetAmount(0)
                .issuerType(IssuerType.MAKER).issuerId(sellerId).targetScope(CouponTargetScope.PROJECT)
                .targetRefId("123").minFundingAmount(30_000).perMemberLimit(1).remainingQuantity(200)
                .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS)).issueChannel(IssueChannel.GENERAL).version(0).build();
        when(makerCouponIssueService.issue(any(), any())).thenReturn(coupon);

        // when & then
        mockMvc.perform(post("/api/v1/sellers/coupons")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content("""
                                {
                                  "projectId": 123, "couponName": "오픈 기념 할인", "discountType": "RATE",
                                  "discountValue": 10, "maxDiscountAmount": 5000, "budgetLimit": 1000000,
                                  "quantity": 200, "minFundingAmount": 30000, "perMemberLimit": 1,
                                  "expiresAt": "2099-12-31T23:59:59Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponCode").value("PJT123-A1B2"));
    }
}
