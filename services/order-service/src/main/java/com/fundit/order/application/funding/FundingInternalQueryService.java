package com.fundit.order.application.funding;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.domain.funding.Funding;
import com.fundit.order.domain.funding.FundingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 내부 전용 조회 — payment/fulfillment-service가 동기 호출하는 최소 필드만 제공한다.
 * PAYMENT-001용 전체 계약(finalAmount/orderName/couponIssuanceId 등)은 별도 연동 이슈이며,
 * 여기서는 fulfillment-service가 실제로 쓰는 projectId/memberId/fundingPublicId만 다룬다.
 */
@Service
@RequiredArgsConstructor
public class FundingInternalQueryService {

    private final FundingRepository fundingRepository;

    @Transactional(readOnly = true)
    public FundingSnapshot getSnapshot(Long fundingId) {
        Funding funding = fundingRepository.findById(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return new FundingSnapshot(funding.getProjectId(), funding.getMemberId(), funding.getPublicId());
    }

    /** 알림 팬아웃 대상 — 펀딩 성립 후 아직 환불되지 않은 참여자의 memberId 목록. */
    @Transactional(readOnly = true)
    public List<UUID> listGoalAchievedParticipantMemberIds(Long projectId) {
        return fundingRepository.findGoalAchievedByProjectId(projectId).stream()
                .map(Funding::getMemberId)
                .toList();
    }

    public record FundingSnapshot(Long projectId, UUID memberId, UUID fundingPublicId) {
    }
}
