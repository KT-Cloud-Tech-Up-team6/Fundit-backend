package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.payment.application.refund.PostShipmentRefundRequestService;
import com.fundit.payment.application.refund.RefundQueryService;
import com.fundit.payment.application.refund.ShippingDelayRefundService;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.presentation.dto.DefectRefundRequestResponse;
import com.fundit.payment.presentation.dto.DefectRefundRequestV2;
import com.fundit.payment.presentation.dto.ExchangeRequestResponse;
import com.fundit.payment.presentation.dto.ExchangeRequestV2;
import com.fundit.payment.presentation.dto.PageResponse;
import com.fundit.payment.presentation.dto.RefundSummaryResponseV2;
import com.fundit.payment.presentation.dto.ReturnRequestResponse;
import com.fundit.payment.presentation.dto.ReturnRequestV2;
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
    private final PostShipmentRefundRequestService postShipmentRefundRequestService;
    private final ShippingDelayRefundService shippingDelayRefundService;

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
        var result = postShipmentRefundRequestService.request(user.id(), request.fundingId(),
                RefundTriggerType.DEFECT, request.toReasonDetail(), request.evidenceUrls());
        return ResponseEntity.status(HttpStatus.CREATED).body(DefectRefundRequestResponse.from(result));
    }

    @PostMapping("/shipping-delay")
    public ResponseEntity<ShippingDelayRefundResponse> requestShippingDelay(
            @LoginUser CurrentUser user, @Valid @RequestBody ShippingDelayRefundRequestV2 request) {
        var result = shippingDelayRefundService.requestCancel(user.id(), request.fundingId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ShippingDelayRefundResponse.from(result));
    }

    /**
     * 발송 후(수령 후) 구매자 귀책 반품 신청 — 단순변심·옵션 선택 오류(환불 정책 V.1.0). 수령 후
     * 7일 이내만 접수하고, 판매자가 회수를 확인해 승인({@code PATCH /api/v1/refunds/{refundId}/decision})
     * 하는 시점에 반품 배송비를 뺀 금액이 부분취소된다.
     *
     * <p>성립 후 **발송 전** 단순변심 취소는 정책상 불가라 별도 경로가 없다(발송 지연일 때만
     * {@code POST /api/v2/refunds/shipping-delay}로 취소할 수 있다).
     */
    @PostMapping("/return")
    public ResponseEntity<ReturnRequestResponse> requestReturn(@LoginUser CurrentUser user,
                                                                @Valid @RequestBody ReturnRequestV2 request) {
        var result = postShipmentRefundRequestService.request(user.id(), request.fundingId(),
                RefundTriggerType.RETURN_CHANGE_OF_MIND, request.toReasonDetail(), request.evidenceUrls());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReturnRequestResponse.from(result));
    }

    /**
     * 교환 신청 — 판매자 검토 대기 상태(REQUESTED)로만 접수한다. 교환은 환불(결제취소)이 아니라
     * 재발송이 필요한 별개 흐름이라 fulfillment-service 연동이 필요한 승인/완료 처리는 아직
     * 없다 — 신청·목록조회(GET /api/v2/refunds?triggerType=EXCHANGE)까지만 이번 범위다.
     *
     * <p>{@code exchangeReason}이 교환 배송비 부담 주체를 정한다. 구매자 귀책이면 5,000원을
     * 별도 결제해야 하지만, 실제 수납은 판매자 승인 시점이라 여기서는 금액만 응답에 담는다.
     */
    @PostMapping("/exchange")
    public ResponseEntity<ExchangeRequestResponse> requestExchange(@LoginUser CurrentUser user,
                                                                     @Valid @RequestBody ExchangeRequestV2 request) {
        var result = postShipmentRefundRequestService.request(user.id(), request.fundingId(),
                RefundTriggerType.EXCHANGE, request.toReasonDetail(), request.evidenceUrls());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ExchangeRequestResponse.from(result, request.exchangeReason()));
    }
}
