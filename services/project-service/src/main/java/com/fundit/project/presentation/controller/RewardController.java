package com.fundit.project.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.project.application.reward.RewardQueryService;
import com.fundit.project.application.reward.RewardService;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardOptionGroup;
import com.fundit.project.presentation.dto.RewardConsumerResponse;
import com.fundit.project.presentation.dto.RewardCreateRequest;
import com.fundit.project.presentation.dto.RewardOptionGroupResponse;
import com.fundit.project.presentation.dto.RewardOptionRequest;
import com.fundit.project.presentation.dto.RewardOptionValueResponse;
import com.fundit.project.presentation.dto.RewardRefundPolicyRequest;
import com.fundit.project.presentation.dto.RewardRefundPolicyResponse;
import com.fundit.project.presentation.dto.RewardResponse;
import com.fundit.project.presentation.dto.RewardUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;
import java.util.UUID;

/** PROJECT-007~009, PROJECT-028 — 리워드 등록/수정/삭제/환불정책, 소비자 조회(PROJECT-008/027 고시 관련은 MVP 범위 제외). */
@Tag(name = "reward")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RewardController {

    private final RewardService rewardService;
    private final RewardQueryService rewardQueryService;

    @Operation(summary = "리워드 등록", description = "has_option=true면 옵션 그룹/값을 함께 등록한다.")
    @ApiResponse(responseCode = "201", description = "생성됨")
    @PostMapping("/projects/{projectId}/rewards")
    public ResponseEntity<RewardResponse> create(
            @LoginUser CurrentUser user, @PathVariable UUID projectId,
            @Valid @RequestBody RewardCreateRequest request) {
        Reward reward = rewardService.create(user.id(), projectId, new RewardService.CreateRewardCommand(
                request.name(), request.description(), request.imageUrl(), request.price(),
                request.isLimited(), request.quantity(), Boolean.TRUE.equals(request.isEarlyBird()),
                toOptionGroups(request.options())));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(reward));
    }

    @Operation(summary = "리워드 수정")
    @PatchMapping("/rewards/{rewardId}")
    public RewardResponse update(
            @LoginUser CurrentUser user, @PathVariable Long rewardId,
            @Valid @RequestBody RewardUpdateRequest request) {
        Reward reward = rewardService.update(user.id(), rewardId, new RewardService.UpdateRewardCommand(
                request.name(), request.description(), request.imageUrl(), request.price(),
                request.isLimited(), request.quantity(), request.isEarlyBird(),
                toOptionGroups(request.options())));
        return toResponse(reward);
    }

    @Operation(summary = "리워드 삭제",
            description = "소프트 딜리트 — 소비자 응답에서 제외되며 물리 삭제하지 않는다.")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    @DeleteMapping("/rewards/{rewardId}")
    public ResponseEntity<Void> delete(@LoginUser CurrentUser user, @PathVariable Long rewardId) {
        rewardService.delete(user.id(), rewardId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "리워드 간편환불 가능 여부 수정")
    @PatchMapping("/rewards/{rewardId}/refund-policy")
    public RewardRefundPolicyResponse updateRefundPolicy(
            @LoginUser CurrentUser user, @PathVariable Long rewardId,
            @Valid @RequestBody RewardRefundPolicyRequest request) {
        Reward reward = rewardService.updateRefundPolicy(user.id(), rewardId, request.simpleRefundDisabled());
        return new RewardRefundPolicyResponse(reward.getId(), reward.isSimpleRefundDisabled());
    }

    @Operation(summary = "리워드 목록 조회(소비자)",
            description = "옵션/잔여재고/품절 여부를 포함해 조회한다. 잔여재고는 order-service 조회 결과다.")
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
