package com.fundit.project.application.port;

import java.util.List;
import java.util.UUID;

/**
 * 펀딩 참여(order-service funding/funding_line_items) 조회 포트.
 * 이 서비스는 펀딩 데이터를 소유하지 않으므로 삭제 가드·집계·후기 자격 검증을 전부 위임한다.
 */
public interface FundingPort {

    boolean hasFundingForReward(Long rewardId);

    boolean hasFundingForOption(Long rewardOptionId);

    /** 후기 작성 자격: 해당 펀딩이 요청자 소유이고 배송완료 상태인지. */
    ReviewEligibility checkReviewEligibility(Long fundingId, UUID memberId);

    FundingStats findStats(Long projectId);

    record ReviewEligibility(boolean ownedByMember, boolean delivered) {
    }

    record FundingStats(long currentAmount, int participantCount, List<RewardSale> rewardSales) {

        public static FundingStats empty() {
            return new FundingStats(0L, 0, List.of());
        }
    }

    record RewardSale(Long rewardOptionId, String optionName, int soldQuantity) {
    }
}
