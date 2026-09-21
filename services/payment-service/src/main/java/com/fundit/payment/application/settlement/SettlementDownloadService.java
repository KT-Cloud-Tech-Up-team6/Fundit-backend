package com.fundit.payment.application.settlement;

import com.fundit.payment.application.settlement.OrderSettlementAggregateClient.LineItemAggregate;
import com.fundit.payment.application.settlement.SettlementQueryService.SettlementDetail;
import com.fundit.payment.domain.settlement.SettlementBatch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * PAYMENT-010 — 정산 내역서 다운로드. PDF/엑셀 라이브러리 도입 대신 CSV로 제공한다(테스트용
 * 정산 파일 요구사항 충족 + 신규 의존성 없음). 조회/권한 검증은 PAYMENT-009({@link SettlementQueryService})를 재사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementDownloadService {

    private final SettlementQueryService settlementQueryService;

    public byte[] download(UUID accountId, Long settlementBatchId) {
        SettlementDetail detail = settlementQueryService.getDetail(accountId, settlementBatchId);
        SettlementBatch batch = detail.batch();

        StringBuilder csv = new StringBuilder();
        csv.append("settlementBatchId,batchType,status,periodStart,periodEnd,grossAmount,platformFeeAmount,")
                .append("refundDeductionAmount,couponDeductionAmount,totalAmount\n");
        csv.append(batch.getId()).append(',').append(batch.getBatchType()).append(',').append(batch.getStatus())
                .append(',').append(batch.getPeriodStart()).append(',').append(batch.getPeriodEnd()).append(',')
                .append(batch.getGrossAmount()).append(',').append(batch.getPlatformFeeAmount()).append(',')
                .append(batch.getRefundDeductionAmount()).append(',').append(batch.getCouponDeductionAmount())
                .append(',').append(batch.getTotalAmount()).append('\n');

        csv.append('\n').append("rewardId,rewardName,optionName,quantity,amount\n");
        for (LineItemAggregate item : detail.lineItems()) {
            csv.append(item.rewardId()).append(',').append(escape(item.rewardName())).append(',')
                    .append(escape(item.optionName())).append(',').append(item.quantity()).append(',')
                    .append(item.amount()).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace(",", " ").replace("\n", " ");
    }
}
