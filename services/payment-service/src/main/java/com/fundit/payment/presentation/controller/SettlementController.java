package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.payment.application.settlement.SettlementDisputeService;
import com.fundit.payment.application.settlement.SettlementDownloadService;
import com.fundit.payment.application.settlement.SettlementQueryService;
import com.fundit.payment.presentation.dto.PageResponse;
import com.fundit.payment.presentation.dto.SettlementBatchSummaryResponse;
import com.fundit.payment.presentation.dto.SettlementDetailResponse;
import com.fundit.payment.presentation.dto.SettlementDisputeRequest;
import com.fundit.payment.presentation.dto.SettlementDisputeResponse;
import com.fundit.payment.presentation.dto.SettlementDisputeSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementQueryService settlementQueryService;
    private final SettlementDownloadService settlementDownloadService;
    private final SettlementDisputeService settlementDisputeService;

    /** PAYMENT-009 변형 — 정산 목록. settlementBatchId를 확인해 상세 조회로 이어간다. */
    @GetMapping
    public PageResponse<SettlementBatchSummaryResponse> list(@LoginUser CurrentUser user,
                                                               @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(
                settlementQueryService.listForSeller(user.id(), pageable).map(SettlementBatchSummaryResponse::from));
    }

    /** PAYMENT-009 — 정산 내역서 조회. */
    @GetMapping("/{settlementBatchId}")
    public SettlementDetailResponse detail(@LoginUser CurrentUser user, @PathVariable Long settlementBatchId) {
        return SettlementDetailResponse.from(settlementQueryService.getDetail(user.id(), settlementBatchId));
    }

    /** PAYMENT-010 — 정산 내역서 다운로드(CSV). */
    @GetMapping("/{settlementBatchId}/download")
    public ResponseEntity<byte[]> download(@LoginUser CurrentUser user, @PathVariable Long settlementBatchId) {
        byte[] csv = settlementDownloadService.download(user.id(), settlementBatchId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"settlement-" + settlementBatchId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    /** PAYMENT-011 — 정산 이의 신청. */
    @PostMapping("/{settlementBatchId}/disputes")
    public ResponseEntity<SettlementDisputeResponse> dispute(@LoginUser CurrentUser user,
                                                               @PathVariable Long settlementBatchId,
                                                               @Valid @RequestBody SettlementDisputeRequest request) {
        var result = settlementDisputeService.create(user.id(), settlementBatchId, request.reason(),
                request.evidenceUrls());
        return ResponseEntity.status(HttpStatus.CREATED).body(SettlementDisputeResponse.from(result));
    }

    /** PAYMENT-011 변형 — 접수한 이의신청 목록·처리 상태 조회. */
    @GetMapping("/disputes")
    public PageResponse<SettlementDisputeSummaryResponse> listDisputes(@LoginUser CurrentUser user,
                                                                        @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(settlementDisputeService.listForSeller(user.id(), pageable)
                .map(SettlementDisputeSummaryResponse::from));
    }

    /** PAYMENT-011 변형 — 이의신청 상세(본인 접수 건만, S4). */
    @GetMapping("/disputes/{disputeId}")
    public SettlementDisputeSummaryResponse disputeDetail(@LoginUser CurrentUser user, @PathVariable Long disputeId) {
        return SettlementDisputeSummaryResponse.from(settlementDisputeService.getDetail(user.id(), disputeId));
    }
}
