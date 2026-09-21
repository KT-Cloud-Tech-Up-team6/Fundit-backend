package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.application.media.MediaStorageClient;
import com.fundit.payment.application.refund.DefectRefundDecisionService;
import com.fundit.payment.application.refund.DefectRefundRequestService;
import com.fundit.payment.application.refund.RefundEstimateService;
import com.fundit.payment.application.refund.RefundEvidenceUploadService;
import com.fundit.payment.application.refund.RefundQueryService;
import com.fundit.payment.application.refund.ShippingDelayRefundService;
import com.fundit.payment.presentation.dto.DefectRefundRequest;
import com.fundit.payment.presentation.dto.DefectRefundRequestResponse;
import com.fundit.payment.presentation.dto.MediaUploadUrlResponse;
import com.fundit.payment.presentation.dto.PageResponse;
import com.fundit.payment.presentation.dto.RefundDecisionRequest;
import com.fundit.payment.presentation.dto.RefundDecisionResponse;
import com.fundit.payment.presentation.dto.RefundEstimateResponse;
import com.fundit.payment.presentation.dto.RefundEvidenceUploadUrlRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * PAYMENT-003/006/007/008. v1은 {@code fundingId: Long} 요청 계약을 유지하고, 컨트롤러에서
 * UUID로 해석한 뒤 서비스 레이어를 호출한다. 목록 응답의 fundingId(Long)는 항상 null
 * (결제 도메인이 UUID만 저장) — 실제 값이 필요하면 {@link RefundControllerV2}를 쓸 것.
 * 결정 API는 path의 refundId(Long PK)만 쓰므로 v1에 그대로 둔다.
 */
@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundQueryService refundQueryService;
    private final DefectRefundRequestService defectRefundRequestService;
    private final DefectRefundDecisionService defectRefundDecisionService;
    private final ShippingDelayRefundService shippingDelayRefundService;
    private final OrderFundingClient orderFundingClient;
    private final RefundEvidenceUploadService refundEvidenceUploadService;
    private final RefundEstimateService refundEstimateService;

    /** PAYMENT-003 — 환불 신청/처리 통합 내역 조회. */
    @GetMapping
    public PageResponse<RefundSummaryResponse> list(@LoginUser CurrentUser user,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(refundQueryService.listMyRefunds(user.id(), pageable).map(RefundSummaryResponse::from));
    }

    /** PAYMENT-003 seller 변형 — 판매자 환불 목록. DEFECT 신청만 대상(그 외 유형은 판매자 검토 대상이 아님). */
    @GetMapping("/seller")
    public PageResponse<RefundSummaryResponse> listForSeller(@LoginUser CurrentUser user,
                                                              @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(refundQueryService.listForSeller(user.id(), pageable).map(RefundSummaryResponse::from));
    }

    /** PAYMENT-006 — 하자환불 신청. */
    @PostMapping("/defect")
    public ResponseEntity<DefectRefundRequestResponse> requestDefect(@LoginUser CurrentUser user,
                                                                       @Valid @RequestBody DefectRefundRequest request) {
        UUID orderId = orderFundingClient.fetchByInternalId(request.fundingId()).fundingPublicId();
        var result = defectRefundRequestService.request(user.id(), orderId, request.toReasonDetail(),
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
        UUID orderId = orderFundingClient.fetchByInternalId(request.fundingId()).fundingPublicId();
        var result = shippingDelayRefundService.requestCancel(user.id(), orderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ShippingDelayRefundResponse.from(result));
    }

    /**
     * F09 — 구매자 반품·교환 증빙 업로드 주소 발급. v1/v2 구분 없이 orderId(UUID)만 받는다
     * (신규 기능이라 레거시 Long 계약이 없다).
     */
    @PostMapping("/evidence/upload-url")
    public MediaUploadUrlResponse issueEvidenceUploadUrl(@LoginUser CurrentUser user,
                                                           @Valid @RequestBody RefundEvidenceUploadUrlRequest request) {
        MediaStorageClient.PresignedUpload presigned = refundEvidenceUploadService.issueUploadUrl(
                user.id(), request.orderId(), request.fileName(), request.contentType(), request.fileSize());
        return new MediaUploadUrlResponse(presigned.uploadUrl(), presigned.fileUrl());
    }

    /** R05 — 환불 신청 전 예상 환불액 사전 계산. */
    @GetMapping("/estimate")
    public RefundEstimateResponse estimate(@LoginUser CurrentUser user, @RequestParam UUID orderId) {
        return RefundEstimateResponse.from(refundEstimateService.estimate(user.id(), orderId));
    }
}
