package com.fundit.project.application.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOptionGroup;
import com.fundit.project.domain.reward.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 리워드 등록/수정/삭제·고시·환불정책 특이사항(PROJECT-007~009). */
@Service
@RequiredArgsConstructor
public class RewardService {

    private final ProjectRepository projectRepository;
    private final RewardRepository rewardRepository;
    private final RewardEventPublisher rewardEventPublisher;

    @Transactional
    public Reward create(UUID sellerId, UUID projectPublicId, CreateRewardCommand command) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        Reward reward = Reward.create(project.getId(), command.name(), command.description(), command.imageUrl(),
                command.price(), command.isLimited(), command.quantity(), command.isEarlyBird(), command.optionGroups());
        Reward saved = rewardRepository.save(reward);
        if (reward.getOptionGroups() != null && !reward.getOptionGroups().isEmpty()) {
            rewardRepository.replaceOptions(saved.getId(), reward.getOptionGroups());
        }

        rewardEventPublisher.publishRewardCreated(
                new RewardEventPublisher.RewardCreatedEvent(saved.getId(), project.getId(), saved.isLimited(), saved.getQuantity()));
        return saved;
    }

    @Transactional
    public Reward update(UUID sellerId, Long rewardId, UpdateRewardCommand command) {
        Reward reward = loadOwned(sellerId, rewardId);

        String name = command.name() != null ? command.name() : reward.getName();
        String description = command.description() != null ? command.description() : reward.getDescription();
        String imageUrl = command.imageUrl() != null ? command.imageUrl() : reward.getImageUrl();
        Long price = command.price() != null ? command.price() : reward.getPrice();
        boolean isLimited = command.isLimited() != null ? command.isLimited() : reward.isLimited();
        // quantity 병합: 값이 직접 왔으면 그 값을, isLimited=false로 바뀌면 null(무제한)을,
        // 둘 다 아니면 기존 값을 유지한다 — chk_rewards_quantity와 동일한 정합성 규칙.
        Integer quantity;
        if (command.quantity() != null) {
            quantity = command.quantity();
        } else if (Boolean.FALSE.equals(command.isLimited())) {
            quantity = null;
        } else {
            quantity = reward.getQuantity();
        }
        boolean isEarlyBird = command.isEarlyBird() != null ? command.isEarlyBird() : reward.isEarlyBird();

        reward.changeBasicInfo(name, description, imageUrl, price, isLimited, quantity, isEarlyBird, command.optionGroups());
        Reward saved = rewardRepository.save(reward);
        if (command.optionGroups() != null) {
            rewardRepository.replaceOptions(saved.getId(), command.optionGroups());
        }

        rewardEventPublisher.publishRewardUpdated(
                new RewardEventPublisher.RewardUpdatedEvent(saved.getId(), saved.getProjectId(), saved.isLimited(), saved.getQuantity()));
        return saved;
    }

    @Transactional
    public void delete(UUID sellerId, Long rewardId) {
        Reward reward = loadOwned(sellerId, rewardId);
        reward.delete();
        rewardRepository.save(reward);
    }

    @Transactional
    public Reward updateDisclosure(UUID sellerId, Long rewardId, String categoryType, Map<String, String> disclosure) {
        Reward reward = loadOwned(sellerId, rewardId);
        reward.changeDisclosure(categoryType, disclosure);
        return rewardRepository.save(reward);
    }

    @Transactional
    public Reward updateRefundPolicy(UUID sellerId, Long rewardId, boolean simpleRefundDisabled) {
        Reward reward = loadOwned(sellerId, rewardId);
        reward.changeRefundPolicy(simpleRefundDisabled);
        return rewardRepository.save(reward);
    }

    private Reward loadOwned(UUID sellerId, Long rewardId) {
        Reward reward = rewardRepository.findById(rewardId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Project project = projectRepository.findById(reward.getProjectId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return reward;
    }

    public record CreateRewardCommand(
            String name, String description, String imageUrl, Long price,
            boolean isLimited, Integer quantity, boolean isEarlyBird, List<RewardOptionGroup> optionGroups) {
    }

    public record UpdateRewardCommand(
            String name, String description, String imageUrl, Long price,
            Boolean isLimited, Integer quantity, Boolean isEarlyBird, List<RewardOptionGroup> optionGroups) {
    }
}
