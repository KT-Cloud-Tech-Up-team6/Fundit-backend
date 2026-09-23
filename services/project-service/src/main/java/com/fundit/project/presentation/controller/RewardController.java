package com.fundit.project.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.project.application.reward.RewardQueryService;
import com.fundit.project.application.reward.RewardService;
import com.fundit.project.domain.reward.EarlyBirdDiscountType;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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

    @Operation(summary = "리워드 등록", description = "has_option=true면 옵션 그룹/값을 함께 등록한다. "
            + "{@code Idempotency-Key} 헤더는 선택값이다 — 보내면 같은 키로 재요청했을 때 새 리워드를 만들지 "
            + "않고 기존 리워드를 그대로 돌려준다(201 대신 200).")
    @ApiResponse(responseCode = "201", description = "생성됨")
    @PostMapping("/projects/{projectId}/rewards")
    public ResponseEntity<RewardResponse> create(
            @LoginUser CurrentUser user, @PathVariable UUID projectId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RewardCreateRequest request) {
        validateIdempotencyKey(idempotencyKey);
        String idempotencyRequestHash = idempotencyKey == null ? null : hashRequest(request);
        NormalizedQuantity quantity = normalizeUnlimitedQuantity(request.isLimited(), request.quantity());
        RewardService.RewardCreateResult result = rewardService.create(user.id(), projectId, new RewardService.CreateRewardCommand(
                request.name(), request.description(), request.imageUrl(), request.price(),
                quantity.isLimited(), quantity.quantity(), Boolean.TRUE.equals(request.isEarlyBird()),
                toDiscountType(request.earlyBirdDiscountType()), request.earlyBirdDiscountValue(),
                toOptionGroups(request.options()), request.shippingFee(), request.estimatedDeliveryDays()),
                idempotencyKey, idempotencyRequestHash);
        HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(toResponse(result.reward()));
    }

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    /** idempotency_key 컬럼이 VARCHAR(100)이라 DB 제약과 동일한 길이를 여기서 먼저 검증한다. */
    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return;
        }
        if (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "Idempotency-Key는 공백일 수 없고 " + MAX_IDEMPOTENCY_KEY_LENGTH + "자를 넘을 수 없습니다.");
        }
    }

    /** 같은 Idempotency-Key에 다른 본문이 오는 것을 구분하기 위한 요청 해시(SHA-256). OrderController와 동일 패턴. */
    private String hashRequest(RewardCreateRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(request.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    @Operation(summary = "리워드 수정")
    @PatchMapping("/rewards/{rewardId}")
    public RewardResponse update(
            @LoginUser CurrentUser user, @PathVariable Long rewardId,
            @Valid @RequestBody RewardUpdateRequest request) {
        NormalizedQuantity quantity = normalizeUnlimitedQuantity(request.isLimited(), request.quantity());
        Reward reward = rewardService.update(user.id(), rewardId, new RewardService.UpdateRewardCommand(
                request.name(), request.description(), request.imageUrl(), request.price(),
                quantity.isLimited(), quantity.quantity(), request.isEarlyBird(),
                toDiscountType(request.earlyBirdDiscountType()), request.earlyBirdDiscountValue(),
                toOptionGroups(request.options()), request.shippingFee(), request.estimatedDeliveryDays()));
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
                .map(this::toConsumerResponse)
                .toList();
    }

    @Operation(summary = "리워드 상세 조회(소비자)",
            description = "리워드가 속한 프로젝트가 공개 상태일 때만 조회 가능하다.")
    @GetMapping("/rewards/{rewardId}")
    public RewardConsumerResponse getForConsumer(@PathVariable Long rewardId) {
        return toConsumerResponse(rewardQueryService.getForConsumer(rewardId));
    }

    @Operation(summary = "리워드 목록 조회(판매자)",
            description = "공개 여부와 무관하게 본인 프로젝트의 리워드 목록을 조회한다(DRAFT 포함).")
    @GetMapping("/projects/{projectId}/rewards/mine")
    public List<RewardResponse> listForSeller(@LoginUser CurrentUser user, @PathVariable UUID projectId) {
        return rewardQueryService.listForSeller(user.id(), projectId).stream()
                .map(this::toSellerResponse)
                .toList();
    }

    private static final int UNLIMITED_QUANTITY_SENTINEL = -1;

    /**
     * quantity: -1은 PM이 설명한 "무제한" 표기를 계약으로도 받아주기 위한 별칭이다 — 응답 계약은
     * 그대로 isLimited:false + quantity:null(canonical)만 쓴다. isLimited:true와 함께 오면
     * 모순이라 정규화하지 않고 그대로 흘려보내, 도메인의 {@code quantity>=0} 검증이 자연스럽게
     * INVALID_REWARD_QUANTITY로 거부하게 둔다.
     */
    private NormalizedQuantity normalizeUnlimitedQuantity(Boolean isLimited, Integer quantity) {
        if (quantity != null && quantity == UNLIMITED_QUANTITY_SENTINEL && !Boolean.TRUE.equals(isLimited)) {
            return new NormalizedQuantity(false, null);
        }
        return new NormalizedQuantity(isLimited, quantity);
    }

    private record NormalizedQuantity(Boolean isLimited, Integer quantity) {
    }

    private List<RewardOptionGroup> toOptionGroups(List<RewardOptionRequest> options) {
        if (options == null) return null;
        return options.stream()
                .map(o -> new RewardOptionGroup(o.optionGroupId(), o.groupName(), o.values()))
                .toList();
    }

    private EarlyBirdDiscountType toDiscountType(String earlyBirdDiscountType) {
        return earlyBirdDiscountType == null ? null : EarlyBirdDiscountType.valueOf(earlyBirdDiscountType);
    }

    /**
     * 생성/수정 응답 전용 — {@code groupId}는 {@code replaceOptions}가 반환한, 실제로 영속화된
     * 그룹 ID다(신규 그룹도 새로 부여받은 ID가 채워진다). 값 단위 ID는 그룹 값 목록이 항상 통째로
     * 교체되므로 애초에 없다(RewardOptionRequest 참고).
     */
    private RewardResponse toResponse(Reward reward) {
        List<RewardOptionGroupResponse> options = reward.getOptionGroups() == null ? List.of()
                : reward.getOptionGroups().stream()
                        .map(g -> new RewardOptionGroupResponse(g.id(), g.groupName(),
                                g.values().stream().map(val -> new RewardOptionValueResponse(null, val)).toList()))
                        .toList();
        return new RewardResponse(reward.getId(), reward.getRewardDisplayCode(), reward.getName(),
                reward.getDescription(), reward.getImageUrl(),
                reward.getPrice(), reward.isLimited(), reward.getQuantity(), reward.isHasOption(),
                reward.getSortOrder(), reward.isEarlyBird(),
                reward.getEarlyBirdDiscountType() == null ? null : reward.getEarlyBirdDiscountType().name(),
                reward.getEarlyBirdDiscountValue(), reward.getEarlyBirdDiscountedPrice(),
                reward.getShippingFee(), reward.getEstimatedDeliveryDays(),
                reward.isSimpleRefundDisabled(), options);
    }

    /** 판매자 재편집 조회(GET .../rewards/mine) 전용 — 옵션 분류·값·ID와 환불정책을 그대로 담는다. */
    private RewardResponse toSellerResponse(RewardQueryService.RewardSellerView v) {
        return new RewardResponse(v.rewardId(), v.rewardDisplayCode(), v.name(), v.description(), v.imageUrl(),
                v.price(), v.isLimited(), v.quantity(), v.hasOption(), v.sortOrder(), v.isEarlyBird(),
                v.earlyBirdDiscountType() == null ? null : v.earlyBirdDiscountType().name(),
                v.earlyBirdDiscountValue(), v.earlyBirdDiscountedPrice(),
                v.shippingFee(), v.estimatedDeliveryDays(),
                v.simpleRefundDisabled(), toOptionGroupResponses(v.options()));
    }

    private RewardConsumerResponse toConsumerResponse(RewardQueryService.RewardConsumerView v) {
        return new RewardConsumerResponse(v.rewardId(), v.rewardDisplayCode(), v.name(), v.description(), v.imageUrl(),
                v.price(), v.isEarlyBird(),
                v.earlyBirdDiscountType() == null ? null : v.earlyBirdDiscountType().name(),
                v.earlyBirdDiscountValue(), v.earlyBirdDiscountedPrice(),
                v.isLimited(), v.remainingStock(), toOptionGroupResponses(v.options()),
                v.soldOut(), v.shippingFee(), v.estimatedDeliveryDays());
    }

    private List<RewardOptionGroupResponse> toOptionGroupResponses(List<RewardQueryService.RewardOptionGroupView> groups) {
        return groups.stream()
                .map(g -> new RewardOptionGroupResponse(g.groupId(), g.groupName(),
                        g.values().stream().map(val -> new RewardOptionValueResponse(val.valueId(), val.value())).toList()))
                .toList();
    }
}
