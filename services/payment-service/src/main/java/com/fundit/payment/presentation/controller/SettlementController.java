package com.fundit.payment.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.payment.application.settlement.SettlementDisputeService;
import com.fundit.payment.application.settlement.SettlementDownloadService;
import com.fundit.payment.application.settlement.SettlementQueryService;
import com.fundit.payment.presentation.dto.SettlementDetailResponse;
import com.fundit.payment.presentation.dto.SettlementDisputeRequest;
import com.fundit.payment.presentation.dto.SettlementDisputeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    /** PAYMENT-009 — 정산 내역서 조회. */
    @GetMapping("/{settlementBatchId}")
    public SettlementDetailResponse detail(@LoginUser CurrentUser user, @PathVariable Long settlementBatchId) {
        return SettlementDetailResponse.from(settlementQueryService.getDetail(user.id(), settlementBatchId));
    }

    /**
     * PAYMENT-010 — 정산 내역서 다운로드. 파일 생성 라이브러리가 아직 정해지지 않아
     * ({@link SettlementDownloadService} 참고) 접근 권한 검증 후 503을 반환한다.
     */
    @GetMapping("/{settlementBatchId}/download")
    public ResponseEntity<Void> download(@LoginUser CurrentUser user, @PathVariable Long settlementBatchId) {
        settlementDownloadService.assertDownloadable(user.id(), settlementBatchId);
        return ResponseEntity.noContent().build();
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
}
