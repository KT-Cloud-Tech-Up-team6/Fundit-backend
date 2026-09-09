package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.payment.application.refund.DefectRefundDecisionService;
import com.fundit.payment.application.refund.DefectRefundRequestService;
import com.fundit.payment.application.refund.RefundQueryService;
import com.fundit.payment.application.refund.ShippingDelayRefundService;
import com.fundit.payment.presentation.dto.DefectRefundRequest;
import com.fundit.payment.presentation.dto.DefectRefundRequestResponse;
import com.fundit.payment.presentation.dto.PageResponse;
import com.fundit.payment.presentation.dto.RefundDecisionRequest;
import com.fundit.payment.presentation.dto.RefundDecisionResponse;
import com.fundit.payment.presentation.dto.RefundSummaryResponse;
import com.fundit.payment.presentation.dto.ShippingDelayRefundRequest;
import com.fundit.payment.presentation.dto.ShippingDelayRefundResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundQueryService refundQueryService;
    private final DefectRefundRequestService defectRefundRequestService;
    private final DefectRefundDecisionService defectRefundDecisionService;
    private final ShippingDelayRefundService shippingDelayRefundService;

    /** PAYMENT-003 — 환불 신청/처리 통합 내역 조회. */
    @GetMapping
    public PageResponse<RefundSummaryResponse> list(@LoginUser CurrentUser user,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(refundQueryService.listMyRefunds(user.id(), pageable).map(RefundSummaryResponse::from));
    }

    /** PAYMENT-006 — 하자환불 신청. */
    @PostMapping("/defect")
    public ResponseEntity<DefectRefundRequestResponse> requestDefect(@LoginUser CurrentUser user,
                                                                       @Valid @RequestBody DefectRefundRequest request) {
        var result = defectRefundRequestService.request(user.id(), request.fundingId(), request.toReasonDetail(),
                request.evidenceUrls());
        return ResponseEntity.status(HttpStatus.CREATED).body(DefectRefundRequestResponse.from(result));
    }

    /** PAYMENT-007 — 하자환불 검토/승인/반려(판매자 전용). */
    @PatchMapping("/{refundId}/decision")
    public RefundDecisionResponse decide(@LoginUser CurrentUser user, @PathVariable Long refundId,
                                          @Valid @RequestBody RefundDecisionRequest request) {
        var result = defectRefundDecisionService.decide(user.id(), refundId, request.isApproved(), request.reason());
        return RefundDecisionResponse.from(result);
    }

    /** PAYMENT-008 — 발송지연 결제취소 신청. */
    @PostMapping("/shipping-delay")
    public ResponseEntity<ShippingDelayRefundResponse> requestShippingDelay(
            @LoginUser CurrentUser user, @Valid @RequestBody ShippingDelayRefundRequest request) {
        var result = shippingDelayRefundService.requestCancel(user.id(), request.fundingId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ShippingDelayRefundResponse.from(result));
    }
}
