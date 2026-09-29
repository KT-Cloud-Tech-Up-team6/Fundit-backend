package com.fundit.project.application.project;

import com.fundit.project.domain.fundingstatus.RewardStat;

import java.util.List;
import java.util.UUID;

/**
 * order-service가 발행하는 {@code project.funding-reward-stats-updated.v1} 계약.
 * JSON 필드명은 order {@code FundingRewardStatsPublisher.RewardStatsUpdatedEvent}와 같다.
 * {@code projectId}는 project-service의 publicId(UUID)다 — order-service는 이 서비스의
 * 내부 PK를 모르므로, 여기서 {@code projects.public_id}로 내부 id를 직접 찾아 쓴다.
 *
 * <p>{@code participantCount}는 참여 회원 수(중복 제외, #180에서 추가). 박싱인 이유: 필드가 없던 구버전
 * order 메시지는 null로 들어와 기존 값을 지켜야 한다 — primitive면 0으로 덮인다.
 */
public record ProjectFundingRewardStatsUpdatedEvent(UUID projectId, List<RewardStat> rewardStats,
                                                    Integer participantCount) {
}
