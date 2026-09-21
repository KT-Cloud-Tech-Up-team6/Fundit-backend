package com.fundit.order.presentation.controller;

import com.fundit.order.application.coupon.CouponBoxQueryService;
import com.fundit.order.application.coupon.CouponIssuanceService;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.CouponIssuance;
import com.fundit.order.domain.coupon.CouponRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class CouponControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CouponIssuanceService couponIssuanceService;
    @MockitoBean
    private CouponBoxQueryService couponBoxQueryService;
    @MockitoBean
    private CouponRepository couponRepository;

    private Coupon coupon() {
        return Coupon.builder().id(1L).couponCode("LIVE-XY12").couponName("라이브 쿠폰")
                .discountType(DiscountType.AMOUNT).discountValue(3_000).maxDiscountAmount(5_000L)
                .issuerType(IssuerType.PLATFORM).targetScope(CouponTargetScope.ALL)
                .minFundingAmount(0).perMemberLimit(1).remainingQuantity(5)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).issueChannel(IssueChannel.LIVE).version(0).build();
    }

    @Test
    void 쿠폰을_클레임하면_200을_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        CouponIssuance issuance = CouponIssuance.issue("LIVE-XY12", memberId);
        when(couponIssuanceService.claim(memberId, "LIVE-XY12")).thenReturn(issuance);
        when(couponRepository.findByCouponCode("LIVE-XY12")).thenReturn(java.util.Optional.of(coupon()));

        // when & then
        mockMvc.perform(post("/api/v1/coupons/LIVE-XY12/claim")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issued").value(true));
    }

    @Test
    void 쿠폰함을_조회한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        var item = new CouponBoxQueryService.CouponBoxItem(coupon(), CouponIssuance.issue("LIVE-XY12", memberId));
        when(couponBoxQueryService.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of(item)));

        // when & then
        mockMvc.perform(get("/api/v1/coupons/me")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].couponCode").value("LIVE-XY12"))
                .andExpect(jsonPath("$.content[0].minFundingAmount").value(0))
                .andExpect(jsonPath("$.content[0].perMemberLimit").value(1))
                .andExpect(jsonPath("$.content[0].targetScope").value("ALL"))
                .andExpect(jsonPath("$.content[0].issuerType").value("PLATFORM"))
                .andExpect(jsonPath("$.content[0].maxDiscountAmount").value(5000));
    }
}
