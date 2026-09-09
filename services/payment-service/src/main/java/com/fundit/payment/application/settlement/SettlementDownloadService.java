package com.fundit.payment.application.settlement;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.domain.settlement.SettlementBatch;
import com.fundit.payment.domain.settlement.SettlementBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PAYMENT-010 — 정산 내역서 다운로드. 접근 권한 검증까지는 구현하되, 실제 파일(PDF/엑셀) 생성은
 * 라이브러리가 미정이라(payment-service CLAUDE.md "정책값 확인 필요") 아직 제공하지 않는다.
 * 거짓 URL을 만들어 반환하는 대신 명시적으로 미구현 상태임을 알린다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementDownloadService {

    private final SettlementBatchRepository settlementBatchRepository;

    public void assertDownloadable(UUID accountId, Long settlementBatchId) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!batch.isOwnedBy(accountId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE, "정산 내역서 다운로드 기능은 준비 중입니다.");
    }
}
