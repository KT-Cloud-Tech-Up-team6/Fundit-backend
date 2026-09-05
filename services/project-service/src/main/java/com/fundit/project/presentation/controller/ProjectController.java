package com.fundit.project.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.project.ProjectQueryService;
import com.fundit.project.application.project.ProjectService;
import com.fundit.project.application.project.ProjectStatsService;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.security.CurrentMember;
import com.fundit.project.presentation.dto.CommonRefundPolicyResponse;
import com.fundit.project.presentation.dto.FundingStatusResponse;
import com.fundit.project.presentation.dto.FundingStatusSummaryResponse;
import com.fundit.project.presentation.dto.IntroContentBlockRequest;
import com.fundit.project.presentation.dto.PageResponse;
import com.fundit.project.presentation.dto.PrivacyConsentRequest;
import com.fundit.project.presentation.dto.PrivacyConsentResponse;
import com.fundit.project.presentation.dto.ProjectBasicInfoRequest;
import com.fundit.project.presentation.dto.ProjectBasicInfoResponse;
import com.fundit.project.presentation.dto.ProjectCreateResponse;
import com.fundit.project.presentation.dto.ProjectDetailResponse;
import com.fundit.project.presentation.dto.ProjectListItemResponse;
import com.fundit.project.presentation.dto.ProjectStatusResponse;
import com.fundit.project.presentation.dto.ProjectStoryRequest;
import com.fundit.project.presentation.dto.ProjectStoryResponse;
import com.fundit.project.presentation.dto.RefundPolicyResponse;
import com.fundit.project.presentation.dto.RewardRefundPolicyResponse;
import com.fundit.project.presentation.dto.RewardStatResponse;
import com.fundit.project.presentation.dto.SellerSummaryResponse;
import com.fundit.project.presentation.dto.WishStatsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectService projectService;
    private final ProjectQueryService projectQueryService;
    private final ProjectStatsService projectStatsService;

    @GetMapping
    public PageResponse<ProjectListItemResponse> list(
            @CurrentMember UUID sellerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        ProjectStatus statusFilter = parseStatus(status);

        var result = projectService.list(sellerId, statusFilter, PageRequest.of(page, size))
                .map(p -> new ProjectListItemResponse(p.getProjectId(), p.getProjectDisplayCode(), p.getTitle(),
                        p.getThumbnailUrl(), p.getStatus(), p.getCreatedAt(), p.getFundingDeadline()));
        return PageResponse.from(result);
    }

    @PostMapping
    public ResponseEntity<ProjectCreateResponse> create(@CurrentMember UUID sellerId) {
        Project project = projectService.create(sellerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ProjectCreateResponse(project.getPublicId(), project.getStatus().name()));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@CurrentMember UUID sellerId, @PathVariable UUID projectId) {
        projectService.delete(sellerId, projectId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{projectId}/basic-info")
    public ProjectBasicInfoResponse updateBasicInfo(
            @CurrentMember UUID sellerId, @PathVariable UUID projectId,
            @Valid @RequestBody ProjectBasicInfoRequest request) {
        Project project = projectService.updateBasicInfo(sellerId, projectId, new ProjectService.UpdateBasicInfoCommand(
                request.businessType(), request.categoryMajor(), request.categoryMinor(),
                request.title(), request.goalAmount()));
        return new ProjectBasicInfoResponse(project.getPublicId(),
                project.getBusinessType() == null ? null : project.getBusinessType().name(),
                project.getCategoryMajor(), project.getCategoryMinor(), project.getTitle(),
                project.getGoalAmount(), project.getUpdatedAt());
    }

    @PostMapping("/{projectId}/privacy-consent")
    public PrivacyConsentResponse consentPrivacy(
            @CurrentMember UUID sellerId, @PathVariable UUID projectId,
            @Valid @RequestBody PrivacyConsentRequest request) {
        var consentedAt = projectService.consentPrivacy(sellerId, projectId, request.agreed());
        return new PrivacyConsentResponse(projectId, consentedAt);
    }

    @PostMapping("/{projectId}/submit")
    public ProjectStatusResponse submit(@CurrentMember UUID sellerId, @PathVariable UUID projectId) {
        Project project = projectService.submit(sellerId, projectId);
        return new ProjectStatusResponse(project.getPublicId(), project.getStatus().name());
    }

    @PatchMapping("/{projectId}/story")
    public ProjectStoryResponse updateStory(
            @CurrentMember UUID sellerId, @PathVariable UUID projectId,
            @Valid @RequestBody ProjectStoryRequest request) {
        Project project = projectService.updateStory(sellerId, projectId, new ProjectService.UpdateStoryCommand(
                request.title(), request.coverImageUrl(), toIntroContent(request.introContent())));
        return new ProjectStoryResponse(project.getPublicId(), project.getUpdatedAt());
    }

    @GetMapping("/{projectId}/preview")
    public ProjectDetailResponse preview(@CurrentMember UUID sellerId, @PathVariable UUID projectId) {
        return toDetailResponse(projectQueryService.getPreview(sellerId, projectId));
    }

    @GetMapping("/{projectId}")
    public ProjectDetailResponse getPublicDetail(@PathVariable UUID projectId) {
        return toDetailResponse(projectQueryService.getPublicDetail(projectId));
    }

    @GetMapping("/{projectId}/refund-policy")
    public RefundPolicyResponse getRefundPolicy(@PathVariable UUID projectId) {
        var view = projectQueryService.getRefundPolicy(projectId);
        return new RefundPolicyResponse(
                new CommonRefundPolicyResponse(view.commonPolicy().simpleRefundDeadline(), view.commonPolicy().goalFailedAutoRefund()),
                view.rewardPolicies().stream()
                        .map(r -> new RewardRefundPolicyResponse(r.rewardId(), r.simpleRefundDisabled()))
                        .toList());
    }

    @GetMapping("/{projectId}/funding-status")
    public FundingStatusResponse getFundingStatus(@CurrentMember UUID sellerId, @PathVariable UUID projectId) {
        var view = projectStatsService.getFundingStatus(sellerId, projectId);
        var rewardStats = view.rewardStats().stream()
                .map(r -> new RewardStatResponse(r.rewardId(), r.purchasedQuantity()))
                .toList();
        return new FundingStatusResponse(view.currentAmount(), view.achievementRate(), view.participantCount(),
                view.openNotifyCount(), view.wishCount(), rewardStats, view.remainingDays(), view.lastSyncedAt());
    }

    @GetMapping("/{projectId}/wish-stats")
    public WishStatsResponse getWishStats(@CurrentMember UUID sellerId, @PathVariable UUID projectId) {
        var view = projectStatsService.getWishStats(sellerId, projectId);
        return new WishStatsResponse(view.wishCount(), view.openNotifyCount());
    }

    private ProjectDetailResponse toDetailResponse(ProjectQueryService.ProjectDetailView view) {
        var fundingStatus = view.fundingStatus();
        return new ProjectDetailResponse(view.projectId(), view.title(), view.status(), view.goalAmount(),
                new FundingStatusSummaryResponse(fundingStatus.currentAmount(), fundingStatus.achievementRate(),
                        fundingStatus.participantCount(), fundingStatus.remainingDays()),
                view.hasLiveVerification(),
                new SellerSummaryResponse(view.seller().sellerId(), view.seller().displayName()));
    }

    private ProjectStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ProjectStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "status 값이 올바르지 않습니다: " + status);
        }
    }

    private List<IntroContentBlock> toIntroContent(List<IntroContentBlockRequest> blocks) {
        if (blocks == null) return null;
        return blocks.stream()
                .map(b -> new IntroContentBlock(IntroContentType.valueOf(b.type()), b.value()))
                .toList();
    }
}
