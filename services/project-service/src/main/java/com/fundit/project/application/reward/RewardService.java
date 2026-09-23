package com.fundit.project.application.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.media.MediaCategory;
import com.fundit.project.application.media.MediaUrlValidator;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.reward.EarlyBirdDiscountType;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOptionGroup;
import com.fundit.project.domain.reward.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 리워드 등록/수정/삭제·환불정책 특이사항(PROJECT-007~009). */
@Service
@RequiredArgsConstructor
public class RewardService {

    private final ProjectRepository projectRepository;
    private final RewardRepository rewardRepository;
    private final RewardEventPublisher rewardEventPublisher;
    private final MediaUrlValidator mediaUrlValidator;

    /**
     * {@code idempotencyKey}는 {@code Idempotency-Key} 헤더(선택값) — 없으면 항상 새 리워드를
     * 만든다. 있으면 프로젝트 범위로 조회해 같은 키가 이미 있으면 새로 만들지 않고 기존 리워드를
     * 그대로 돌려준다(order-service OrderCreateService와 동일 패턴). 같은 키에 요청 본문
     * (idempotencyRequestHash)이 다르면 CONFLICT로 거부한다.
     */
    @Transactional
    public RewardCreateResult create(UUID sellerId, UUID projectPublicId, CreateRewardCommand command,
                                      String idempotencyKey, String idempotencyRequestHash) {
        Project project = projectRepository.findByPublicId(projectPublicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        if (idempotencyKey != null) {
            Optional<RewardCreateResult> replay = findReplay(project.getId(), idempotencyKey, idempotencyRequestHash);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        if (command.imageUrl() != null) {
            mediaUrlValidator.validate(project.getPublicId(), command.imageUrl(), MediaCategory.IMAGE);
        }

        Reward reward = Reward.create(project.getId(), command.name(), command.description(), command.imageUrl(),
                command.price(), command.isLimited(), command.quantity(), command.isEarlyBird(),
                command.earlyBirdDiscountType(), command.earlyBirdDiscountValue(), command.optionGroups(),
                command.shippingFee(), command.estimatedDeliveryDays(), idempotencyKey, idempotencyRequestHash);
        Reward saved;
        try {
            saved = rewardRepository.save(reward);
        } catch (DataIntegrityViolationException e) {
            // uq_rewards_project_idempotency_key 위반 — 동시에 같은 키로 들어온 다른 요청이 먼저
            // 커밋됨(OrderCreateService.create와 동일 레이스 처리).
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "동일한 Idempotency-Key로 처리 중인 요청이 있습니다. 잠시 후 다시 시도하세요.");
        }
        if (reward.getOptionGroups() != null && !reward.getOptionGroups().isEmpty()) {
            List<RewardOptionGroup> persistedOptions = rewardRepository.replaceOptions(saved.getId(), reward.getOptionGroups());
            saved = saved.toBuilder().optionGroups(persistedOptions).build();
        }

        rewardEventPublisher.publishRewardCreated(
                new RewardEventPublisher.RewardCreatedEvent(saved.getId(), project.getId(), saved.isLimited(), saved.getQuantity()));
        return new RewardCreateResult(saved, false);
    }

    /** 같은 프로젝트의 같은 키로 이미 만든 리워드가 있으면 재생성/이벤트 재발행 없이 그대로 돌려준다. */
    private Optional<RewardCreateResult> findReplay(Long projectId, String idempotencyKey, String idempotencyRequestHash) {
        return rewardRepository.findByProjectIdAndIdempotencyKey(projectId, idempotencyKey).map(existing -> {
            if (!Objects.equals(existing.getIdempotencyRequestHash(), idempotencyRequestHash)) {
                throw new BusinessException(CommonErrorCode.CONFLICT,
                        "동일한 Idempotency-Key로 다른 내용의 요청이 감지되었습니다.");
            }
            return new RewardCreateResult(existing, true);
        });
    }

    /** {@code replay=true}면 이번 호출로 새로 만든 리워드가 아니라 같은 키의 기존 리워드를 그대로 반환한 것이다. */
    public record RewardCreateResult(Reward reward, boolean replay) {
    }

    @Transactional
    public Reward update(UUID sellerId, Long rewardId, UpdateRewardCommand command) {
        OwnedReward owned = loadOwnedForUpdate(sellerId, rewardId);
        Reward reward = owned.reward();
        if (command.imageUrl() != null) {
            mediaUrlValidator.validate(owned.project().getPublicId(), command.imageUrl(), MediaCategory.IMAGE);
        }

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
        // 할인 방식/값 병합: 필드별로 명시적으로 왔으면 그 값을, isEarlyBird=false로 바뀌면 null을,
        // 둘 다 아니면 기존 값을 유지한다 — quantity와 동일한 병합 패턴.
        EarlyBirdDiscountType earlyBirdDiscountType;
        if (command.earlyBirdDiscountType() != null) {
            earlyBirdDiscountType = command.earlyBirdDiscountType();
        } else if (Boolean.FALSE.equals(command.isEarlyBird())) {
            earlyBirdDiscountType = null;
        } else {
            earlyBirdDiscountType = reward.getEarlyBirdDiscountType();
        }
        Long earlyBirdDiscountValue;
        if (command.earlyBirdDiscountValue() != null) {
            earlyBirdDiscountValue = command.earlyBirdDiscountValue();
        } else if (Boolean.FALSE.equals(command.isEarlyBird())) {
            earlyBirdDiscountValue = null;
        } else {
            earlyBirdDiscountValue = reward.getEarlyBirdDiscountValue();
        }
        Long shippingFee = command.shippingFee() != null ? command.shippingFee() : reward.getShippingFee();
        Integer estimatedDeliveryDays = command.estimatedDeliveryDays() != null
                ? command.estimatedDeliveryDays() : reward.getEstimatedDeliveryDays();

        reward.changeBasicInfo(name, description, imageUrl, price, isLimited, quantity, isEarlyBird,
                earlyBirdDiscountType, earlyBirdDiscountValue, command.optionGroups(),
                shippingFee, estimatedDeliveryDays);
        Reward saved = rewardRepository.save(reward);
        if (command.optionGroups() != null) {
            List<RewardOptionGroup> persistedOptions = rewardRepository.replaceOptions(saved.getId(), command.optionGroups());
            saved = saved.toBuilder().optionGroups(persistedOptions).build();
        }

        rewardEventPublisher.publishRewardUpdated(
                new RewardEventPublisher.RewardUpdatedEvent(saved.getId(), saved.getProjectId(), saved.isLimited(), saved.getQuantity()));
        return saved;
    }

    @Transactional
    public void delete(UUID sellerId, Long rewardId) {
        Reward reward = loadOwned(sellerId, rewardId).reward();
        reward.delete();
        rewardRepository.save(reward);
    }

    @Transactional
    public Reward updateRefundPolicy(UUID sellerId, Long rewardId, boolean simpleRefundDisabled) {
        Reward reward = loadOwned(sellerId, rewardId).reward();
        reward.changeRefundPolicy(simpleRefundDisabled);
        return rewardRepository.save(reward);
    }

    private OwnedReward loadOwned(UUID sellerId, Long rewardId) {
        return requireOwned(sellerId, rewardRepository.findById(rewardId));
    }

    private OwnedReward loadOwnedForUpdate(UUID sellerId, Long rewardId) {
        return requireOwned(sellerId, rewardRepository.findByIdForUpdate(rewardId));
    }

    private OwnedReward requireOwned(UUID sellerId, Optional<Reward> found) {
        Reward reward = found.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        Project project = projectRepository.findById(reward.getProjectId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return new OwnedReward(reward, project);
    }

    private record OwnedReward(Reward reward, Project project) {
    }

    public record CreateRewardCommand(
            String name, String description, String imageUrl, Long price,
            boolean isLimited, Integer quantity, boolean isEarlyBird,
            EarlyBirdDiscountType earlyBirdDiscountType, Long earlyBirdDiscountValue,
            List<RewardOptionGroup> optionGroups, Long shippingFee, Integer estimatedDeliveryDays) {
    }

    public record UpdateRewardCommand(
            String name, String description, String imageUrl, Long price,
            Boolean isLimited, Integer quantity, Boolean isEarlyBird,
            EarlyBirdDiscountType earlyBirdDiscountType, Long earlyBirdDiscountValue,
            List<RewardOptionGroup> optionGroups, Long shippingFee, Integer estimatedDeliveryDays) {
    }
}
