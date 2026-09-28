package com.fundit.order.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.application.coupon.MakerCouponIssueService;
import com.fundit.order.application.live.LiveStatusClient;
import com.fundit.order.domain.coupon.Coupon;
import com.fundit.order.domain.coupon.IssueChannel;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.order.presentation.dto.MakerCouponIssueRequest;
import com.fundit.order.presentation.dto.MakerCouponIssueResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * ORDER-008 — 쿠폰 발급(메이커).
 *
 * <p>cross-service ID 통일(#69) 이후에도 이 엔드포인트는 Long(v1) 계약을 유지한다 — 요청마다
 * project-service 내부 API로 UUID를 먼저 해석한 뒤 UUID 기반 서비스 레이어를 호출한다.
 */
@RestController
@RequestMapping("/api/v1/sellers/coupons")
@RequiredArgsConstructor
public class SellerCouponController {

    private final MakerCouponIssueService makerCouponIssueService;
    private final ProjectOwnershipClient projectOwnershipClient;
    private final LiveStatusClient liveStatusClient;

    @PostMapping
    public ResponseEntity<MakerCouponIssueResponse> issue(@LoginUser CurrentUser user,
                                                            @Valid @RequestBody MakerCouponIssueRequest request) {
        UUID projectPublicId = projectOwnershipClient.findPublicId(request.projectId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        // LIVE 쿠폰만 방송을 조회한다 — GENERAL 쿠폰 발급에는 live 호출이 발생하지 않는다.
        // 조회는 트랜잭션에 들어가기 전(컨트롤러)에서 끝낸다.
        LiveStatusClient.LiveStatus live = request.resolveIssueChannel() == IssueChannel.LIVE
                ? liveStatusClient.findByLiveId(request.liveId())
                        .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "존재하지 않는 방송입니다."))
                : null;
        Coupon coupon = makerCouponIssueService.issue(user.id(), request.toCommand(projectPublicId,
                live == null ? null : live.sessionId(), live == null ? null : live.sellerId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(MakerCouponIssueResponse.from(coupon));
    }
}
