package com.fundit.project.infrastructure.external;

import com.fundit.project.application.port.FundingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * order-service 미연동 상태의 임시 어댑터.
 * <p>
 * 삭제 가드(hasFundingFor*)는 "참여 이력 없음"으로 답한다 — 펀딩 데이터 자체가 존재하지 않는
 * 현재 상태의 사실과 일치한다. 반면 후기 작성 자격은 확인할 방법이 없으므로 두 필드 모두 false다.
 * 소유 여부를 true로 두면 모든 요청이 422(SUPPORTER_REVIEW_NOT_ELIGIBLE)로만 거절되고
 * 비소유 요청의 403(FORBIDDEN) 경로가 사라진다.
 */
@Slf4j
@Component
public class StubFundingAdapter implements FundingPort {

    @Override
    public boolean hasFundingForReward(Long rewardId) {
        return false;
    }

    @Override
    public boolean hasFundingForOption(Long rewardOptionId) {
        return false;
    }

    @Override
    public ReviewEligibility checkReviewEligibility(Long fundingId, UUID memberId) {
        log.warn("후기 자격 검증 불가 - order-service 미연동. fundingId={}", fundingId);
        return new ReviewEligibility(false, false);
    }

    @Override
    public FundingStats findStats(Long projectId) {
        return FundingStats.empty();
    }
}
