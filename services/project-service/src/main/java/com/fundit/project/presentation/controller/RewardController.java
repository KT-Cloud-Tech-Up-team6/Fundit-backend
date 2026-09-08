package com.fundit.project.presentation.controller;

import com.fundit.project.application.reward.RewardQueryService;
import com.fundit.project.application.reward.RewardService;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOptionGroup;
import com.fundit.project.infrastructure.security.CurrentMember;
import com.fundit.project.presentation.dto.RewardConsumerResponse;
import com.fundit.project.presentation.dto.RewardCreateRequest;
import com.fundit.project.presentation.dto.RewardDisclosureListItemResponse;
import com.fundit.project.presentation.dto.RewardDisclosureRequest;
import com.fundit.project.presentation.dto.RewardDisclosureResponse;
import com.fundit.project.presentation.dto.RewardOptionGroupResponse;
import com.fundit.project.presentation.dto.RewardOptionRequest;
import com.fundit.project.presentation.dto.RewardOptionValueResponse;
import com.fundit.project.presentation.dto.RewardRefundPolicyRequest;
import com.fundit.project.presentation.dto.RewardRefundPolicyResponse;
import com.fundit.project.presentation.dto.RewardResponse;
import com.fundit.project.presentation.dto.RewardUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** PROJECT-007~009, PROJECT-027, PROJECT-028 — 리워드 등록/수정/삭제/고시/환불정책, 소비자 조회. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;
    private final RewardQueryService rewardQueryService;

    @PostMapping("/projects/{projectId}/rewards")
    public ResponseEntity<RewardResponse> create(
            @CurrentMember UUID sellerId, @PathVariable UUID projectId,
            @Valid @RequestBody RewardCreateRequest request) {
        Reward reward = rewardService.create(sellerId, projectId, new RewardService.CreateRewardCommand(
                request.name(), request.description(), request.imageUrl(), request.price(),
                request.isLimited(), request.quantity(), Boolean.TRUE.equals(request.isEarlyBird()),
                toOptionGroups(request.options())));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(reward));
    }

    @PatchMapping("/rewards/{rewardId}")
    public RewardResponse update(
            @CurrentMember UUID sellerId, @PathVariable Long rewardId,
            @Valid @RequestBody RewardUpdateRequest request) {
        Reward reward = rewardService.update(sellerId, rewardId, new RewardService.UpdateRewardCommand(
                request.name(), request.description(), request.imageUrl(), request.price(),
                request.isLimited(), request.quantity(), request.isEarlyBird(),
                toOptionGroups(request.options())));
        return toResponse(reward);
    }

    @DeleteMapping("/rewards/{rewardId}")
    public ResponseEntity<Void> delete(@CurrentMember UUID sellerId, @PathVariable Long rewardId) {
        rewardService.delete(sellerId, rewardId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/rewards/{rewardId}/disclosure")
    public RewardDisclosureResponse updateDisclosure(
            @CurrentMember UUID sellerId, @PathVariable Long rewardId,
            @Valid @RequestBody RewardDisclosureRequest request) {
        Reward reward = rewardService.updateDisclosure(sellerId, rewardId, request.categoryType(), request.disclosure());
        return new RewardDisclosureResponse(reward.getId(), reward.getCategoryType(), reward.getDisclosure());
    }

    @PatchMapping("/rewards/{rewardId}/refund-policy")
    public RewardRefundPolicyResponse updateRefundPolicy(
            @CurrentMember UUID sellerId, @PathVariable Long rewardId,
            @Valid @RequestBody RewardRefundPolicyRequest request) {
        Reward reward = rewardService.updateRefundPolicy(sellerId, rewardId, request.simpleRefundDisabled());
        return new RewardRefundPolicyResponse(reward.getId(), reward.isSimpleRefundDisabled());
    }

    @GetMapping("/projects/{projectId}/rewards")
    public List<RewardConsumerResponse> listForConsumer(@PathVariable UUID projectId) {
        return rewardQueryService.listForConsumer(projectId).stream()
                .map(v -> new RewardConsumerResponse(v.rewardId(), v.rewardDisplayCode(), v.name(), v.price(),
                        v.isEarlyBird(), v.isLimited(), v.remainingStock(),
                        v.options().stream()
                                .map(g -> new RewardOptionGroupResponse(g.groupId(), g.groupName(),
                                        g.values().stream().map(val -> new RewardOptionValueResponse(val.valueId(), val.value())).toList()))
                                .toList(),
                        v.soldOut()))
                .toList();
    }

    @GetMapping("/projects/{projectId}/rewards/disclosures")
    public List<RewardDisclosureListItemResponse> listDisclosures(@PathVariable UUID projectId) {
        return rewardQueryService.listDisclosures(projectId).stream()
                .map(v -> new RewardDisclosureListItemResponse(v.rewardId(), v.rewardName(), v.categoryType(), v.disclosure()))
                .toList();
    }

    private List<RewardOptionGroup> toOptionGroups(List<RewardOptionRequest> options) {
        if (options == null) return null;
        return options.stream()
                .map(o -> new RewardOptionGroup(o.groupName(), o.values()))
                .toList();
    }

    private RewardResponse toResponse(Reward reward) {
        return new RewardResponse(reward.getId(), reward.getRewardDisplayCode(), reward.getName(),
                reward.getPrice(), reward.isLimited(), reward.getQuantity(), reward.isHasOption());
    }
}
