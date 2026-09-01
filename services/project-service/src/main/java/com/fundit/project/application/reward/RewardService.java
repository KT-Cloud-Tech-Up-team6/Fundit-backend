package com.fundit.project.application.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RewardService {

    private final RewardRepository rewardRepository;
    private final ProjectAccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;
    private final FundingPort fundingPort;

    /** PROJECT-008. disclosure 필수 항목 검증은 검수 요청 시점으로 미루고 등록은 저장만 한다. */
    public Reward create(UUID projectId,
                         String name,
                         String description,
                         Long price,
                         boolean unlimited,
                         boolean earlyBird,
                         boolean simpleRefundDisabled,
                         String categoryType,
                         Map<String, Object> disclosure) {
        Project project = ownedModifiableProject(projectId);
        Reward reward = Reward.create(project.getId(), name, description, price, unlimited,
                earlyBird, simpleRefundDisabled, categoryType, disclosure);
        return rewardRepository.save(reward);
    }

    /** PROJECT-009. isUnlimited는 재고 정책과 얽혀 있어 이 API로 바꿀 수 없다. */
    public Reward update(UUID projectId,
                         Long rewardId,
                         String name,
                         String description,
                         Long price,
                         Boolean earlyBird,
                         Boolean simpleRefundDisabled,
                         String categoryType,
                         Map<String, Object> disclosure,
                         Integer displayOrder) {
        Project project = ownedModifiableProject(projectId);
        Reward reward = findRewardOf(project, rewardId);
        reward.update(name, description, price, earlyBird, simpleRefundDisabled,
                categoryType, disclosure, displayOrder);
        return rewardRepository.save(reward);
    }

    /** PROJECT-010. 펀딩 참여가 1건이라도 있으면 소프트 삭제도 막는다. */
    public void delete(UUID projectId, Long rewardId) {
        Project project = ownedModifiableProject(projectId);
        Reward reward = findRewardOf(project, rewardId);

        if (fundingPort.hasFundingForReward(reward.getId())) {
            throw new BusinessException(ProjectErrorCode.REWARD_HAS_ACTIVE_FUNDING);
        }

        reward.softDelete(Instant.now());
        rewardRepository.save(reward);
    }

    private Project ownedModifiableProject(UUID projectId) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findOwned(projectId, currentUser);
        project.ensureModifiable();
        return project;
    }

    private Reward findRewardOf(Project project, Long rewardId) {
        Reward reward = rewardRepository.findActiveById(rewardId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!reward.getProjectId().equals(project.getId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return reward;
    }
}
