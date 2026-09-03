package com.fundit.project.presentation.controller;

import com.fundit.project.application.reward.RewardOptionService;
import com.fundit.project.application.reward.RewardQueryService;
import com.fundit.project.application.reward.RewardService;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOption;
import com.fundit.project.presentation.dto.common.MessageResponse;
import com.fundit.project.presentation.dto.reward.RewardCreateRequest;
import com.fundit.project.presentation.dto.reward.RewardCreatedResponse;
import com.fundit.project.presentation.dto.reward.RewardDisclosureResponse;
import com.fundit.project.presentation.dto.reward.RewardIdResponse;
import com.fundit.project.presentation.dto.reward.RewardOptionCreateRequest;
import com.fundit.project.presentation.dto.reward.RewardOptionIdResponse;
import com.fundit.project.presentation.dto.reward.RewardOptionUpdateRequest;
import com.fundit.project.presentation.dto.reward.RewardUpdateRequest;
import com.fundit.project.presentation.dto.reward.RewardWithOptionsResponse;
import com.fundit.project.presentation.dto.reward.RewardsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}")
public class RewardController {

    private final RewardService rewardService;
    private final RewardOptionService rewardOptionService;
    private final RewardQueryService rewardQueryService;

    @PostMapping("/rewards")
    public ResponseEntity<RewardCreatedResponse> createReward(@PathVariable UUID projectId,
                                                              @Valid @RequestBody RewardCreateRequest request) {
        Reward reward = rewardService.create(
                projectId,
                request.name(),
                request.description(),
                request.price(),
                Boolean.TRUE.equals(request.unlimited()),
                request.earlyBirdOrDefault(),
                request.simpleRefundDisabledOrDefault(),
                request.categoryType(),
                request.disclosure());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RewardCreatedResponse(reward.getId(), projectId));
    }

    @PatchMapping("/rewards/{rewardId}")
    public RewardIdResponse updateReward(@PathVariable UUID projectId,
                                         @PathVariable Long rewardId,
                                         @RequestBody RewardUpdateRequest request) {
        Reward reward = rewardService.update(
                projectId,
                rewardId,
                request.name(),
                request.description(),
                request.price(),
                request.earlyBird(),
                request.simpleRefundDisabled(),
                request.categoryType(),
                request.disclosure(),
                request.displayOrder());
        return new RewardIdResponse(reward.getId());
    }

    @DeleteMapping("/rewards/{rewardId}")
    public MessageResponse deleteReward(@PathVariable UUID projectId, @PathVariable Long rewardId) {
        rewardService.delete(projectId, rewardId);
        return MessageResponse.ok();
    }

    @PostMapping("/rewards/{rewardId}/options")
    public ResponseEntity<RewardOptionIdResponse> createOption(
            @PathVariable UUID projectId,
            @PathVariable Long rewardId,
            @Valid @RequestBody RewardOptionCreateRequest request) {

        RewardOption option = rewardOptionService.create(
                projectId, rewardId, request.optionName(), request.sku(), request.initialStock());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RewardOptionIdResponse(option.getId()));
    }

    @PatchMapping("/rewards/{rewardId}/options/{optionId}")
    public RewardOptionIdResponse updateOption(@PathVariable UUID projectId,
                                               @PathVariable Long rewardId,
                                               @PathVariable Long optionId,
                                               @RequestBody RewardOptionUpdateRequest request) {
        RewardOption option = rewardOptionService.rename(projectId, rewardId, optionId, request.optionName());
        return new RewardOptionIdResponse(option.getId());
    }

    @DeleteMapping("/rewards/{rewardId}/options/{optionId}")
    public MessageResponse deleteOption(@PathVariable UUID projectId,
                                        @PathVariable Long rewardId,
                                        @PathVariable Long optionId) {
        rewardOptionService.delete(projectId, rewardId, optionId);
        return MessageResponse.ok();
    }

    @GetMapping("/rewards")
    public RewardsResponse<RewardWithOptionsResponse> listRewards(@PathVariable UUID projectId) {
        return RewardsResponse.of(rewardQueryService.listWithStock(projectId).stream()
                .map(RewardWithOptionsResponse::from)
                .toList());
    }

    @GetMapping("/reward-info")
    public RewardsResponse<RewardDisclosureResponse> listRewardInfo(@PathVariable UUID projectId) {
        return RewardsResponse.of(rewardQueryService.listDisclosures(projectId).stream()
                .map(RewardDisclosureResponse::from)
                .toList());
    }
}
