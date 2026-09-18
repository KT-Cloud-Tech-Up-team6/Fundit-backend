package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.payment.application.refund.DefectRefundRequestService;
import com.fundit.payment.application.refund.RefundQueryService;
import com.fundit.payment.application.refund.ShippingDelayRefundService;
import com.fundit.payment.presentation.dto.DefectRefundRequestResponse;
import com.fundit.payment.presentation.dto.DefectRefundRequestV2;
import com.fundit.payment.presentation.dto.PageResponse;
import com.fundit.payment.presentation.dto.RefundSummaryResponseV2;
import com.fundit.payment.presentation.dto.ShippingDelayRefundRequestV2;
import com.fundit.payment.presentation.dto.ShippingDelayRefundResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PAYMENT-003/006/008 v2 — fundingId를 order-service publicId(UUID)로 그대로 받는다/돌려준다.
 * 하자환불 결정({@code PATCH /{refundId}/decision})은 path의 refundId만 쓰므로 v1에 둔다.
 */
@RestController
@RequestMapping("/api/v2/refunds")
@RequiredArgsConstructor
public class RefundControllerV2 {

    private final RefundQueryService refundQueryService;
    private final DefectRefundRequestService defectRefundRequestService;
    private final ShippingDelayRefundService shippingDelayRefundService;

    @GetMapping
    public PageResponse<RefundSummaryResponseV2> list(@LoginUser CurrentUser user,
                                                       @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(refundQueryService.listMyRefunds(user.id(), pageable)
                .map(RefundSummaryResponseV2::from));
    }

    @PostMapping("/defect")
    public ResponseEntity<DefectRefundRequestResponse> requestDefect(@LoginUser CurrentUser user,
                                                                       @Valid @RequestBody DefectRefundRequestV2 request) {
        var result = defectRefundRequestService.request(user.id(), request.fundingId(), request.toReasonDetail(),
                request.evidenceUrls());
        return ResponseEntity.status(HttpStatus.CREATED).body(DefectRefundRequestResponse.from(result));
    }

    @PostMapping("/shipping-delay")
    public ResponseEntity<ShippingDelayRefundResponse> requestShippingDelay(
            @LoginUser CurrentUser user, @Valid @RequestBody ShippingDelayRefundRequestV2 request) {
        var result = shippingDelayRefundService.requestCancel(user.id(), request.fundingId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ShippingDelayRefundResponse.from(result));
    }
}
