package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.payment.application.refund.DefectRefundRequestService;
import com.fundit.payment.application.refund.ExchangeRequestService;
import com.fundit.payment.application.refund.RefundQueryService;
import com.fundit.payment.application.refund.ShippingDelayRefundService;
import com.fundit.payment.application.refund.SimpleChangeOfMindRefundService;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.presentation.dto.DefectRefundRequestResponse;
import com.fundit.payment.presentation.dto.DefectRefundRequestV2;
import com.fundit.payment.presentation.dto.ExchangeRequestResponse;
import com.fundit.payment.presentation.dto.ExchangeRequestV2;
import com.fundit.payment.presentation.dto.PageResponse;
import com.fundit.payment.presentation.dto.RefundSummaryResponseV2;
import com.fundit.payment.presentation.dto.ShippingDelayRefundRequestV2;
import com.fundit.payment.presentation.dto.ShippingDelayRefundResponse;
import com.fundit.payment.presentation.dto.SimpleChangeOfMindRefundRequestV2;
import com.fundit.payment.presentation.dto.SimpleChangeOfMindRefundResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    private final SimpleChangeOfMindRefundService simpleChangeOfMindRefundService;
    private final ExchangeRequestService exchangeRequestService;

    @GetMapping
    public PageResponse<RefundSummaryResponseV2> list(@LoginUser CurrentUser user,
                                                       @RequestParam(required = false) RefundTriggerType triggerType,
                                                       @RequestParam(required = false) Boolean inProgress,
                                                       @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(refundQueryService.listMyRefunds(user.id(), triggerType, inProgress, pageable)
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

    /**
     * 성립(GOAL_ACHIEVED) 이후 발송 전 단순변심 환불 — 모금 진행 중 참여 취소
     * ({@code POST /api/v1/orders/{orderId}/cancel}, order-service ORDER-014)와는 별개 흐름이다.
     * 이미 발송이 시작됐으면 반품 절차가 필요해 이 엔드포인트로는 처리하지 않는다(ALREADY_SHIPPED).
     */
    @PostMapping("/simple-change-of-mind")
    public ResponseEntity<SimpleChangeOfMindRefundResponse> requestSimpleChangeOfMind(
            @LoginUser CurrentUser user, @Valid @RequestBody SimpleChangeOfMindRefundRequestV2 request) {
        var result = simpleChangeOfMindRefundService.requestCancel(user.id(), request.fundingId());
        return ResponseEntity.status(HttpStatus.CREATED).body(SimpleChangeOfMindRefundResponse.from(result));
    }

    /**
     * 교환 신청 — 판매자 검토 대기 상태(REQUESTED)로만 접수한다. 교환은 환불(결제취소)이 아니라
     * 재발송이 필요한 별개 흐름이라 fulfillment-service 연동이 필요한 승인/완료 처리는 아직
     * 없다 — 신청·목록조회(GET /api/v2/refunds?triggerType=EXCHANGE)까지만 이번 범위다.
     */
    @PostMapping("/exchange")
    public ResponseEntity<ExchangeRequestResponse> requestExchange(@LoginUser CurrentUser user,
                                                                     @Valid @RequestBody ExchangeRequestV2 request) {
        var result = exchangeRequestService.request(user.id(), request.fundingId(), request.reasonDetail(),
                request.evidenceUrls());
        return ResponseEntity.status(HttpStatus.CREATED).body(ExchangeRequestResponse.from(result));
    }
}
