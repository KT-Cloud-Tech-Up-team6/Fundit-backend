package com.fundit.project.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.project.application.project.ProjectQueryService;
import com.fundit.project.application.project.ProjectService;
import com.fundit.project.application.project.ProjectStatsService;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectStatus;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "project")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectService projectService;
    private final ProjectQueryService projectQueryService;
    private final ProjectStatsService projectStatsService;

    @Operation(summary = "판매자 프로젝트 목록 조회",
            description = "로그인한 판매자 본인의 프로젝트를 상태별로 페이지네이션 조회한다. status 미지정 시 전체 상태.")
    @GetMapping
    public PageResponse<ProjectListItemResponse> list(
            @LoginUser CurrentUser user,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        ProjectStatus statusFilter = parseStatus(status);

        var result = projectService.list(user.id(), statusFilter, PageRequest.of(page, size))
                .map(p -> new ProjectListItemResponse(p.getProjectId(), p.getProjectDisplayCode(), p.getTitle(),
                        p.getThumbnailUrl(), p.getStatus(), p.getCreatedAt(), p.getFundingDeadline()));
        return PageResponse.from(result);
    }

    @Operation(summary = "프로젝트 생성(DRAFT)",
            description = "빈 DRAFT 프로젝트를 생성한다. 이후 basic-info/story 등 단계별 API로 채워나간다.")
    @ApiResponse(responseCode = "201", description = "생성됨")
    @PostMapping
    public ResponseEntity<ProjectCreateResponse> create(@LoginUser CurrentUser user) {
        Project project = projectService.create(user.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ProjectCreateResponse(project.getPublicId(), project.getStatus().name()));
    }

    @Operation(summary = "프로젝트 삭제", description = "DRAFT 상태의 프로젝트만 삭제할 수 있다(PROJECT_NOT_DELETABLE).")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@LoginUser CurrentUser user, @PathVariable UUID projectId) {
        projectService.delete(user.id(), projectId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "기본정보 수정", description = "사업자 유형/카테고리/제목/목표금액을 수정한다.")
    @PatchMapping("/{projectId}/basic-info")
    public ProjectBasicInfoResponse updateBasicInfo(
            @LoginUser CurrentUser user, @PathVariable UUID projectId,
            @Valid @RequestBody ProjectBasicInfoRequest request) {
        Project project = projectService.updateBasicInfo(user.id(), projectId, new ProjectService.UpdateBasicInfoCommand(
                request.businessType(), request.categoryMajor(), request.categoryMinor(),
                request.title(), request.goalAmount()));
        return new ProjectBasicInfoResponse(project.getPublicId(),
                project.getBusinessType() == null ? null : project.getBusinessType().name(),
                project.getCategoryMajor(), project.getCategoryMinor(), project.getTitle(),
                project.getGoalAmount(), project.getUpdatedAt());
    }

    @Operation(summary = "개인정보 수집 동의",
            description = "심사 제출 전 개인정보 수집 동의를 기록한다(PRIVACY_CONSENT_REQUIRED 전제조건).")
    @PostMapping("/{projectId}/privacy-consent")
    public PrivacyConsentResponse consentPrivacy(
            @LoginUser CurrentUser user, @PathVariable UUID projectId,
            @Valid @RequestBody PrivacyConsentRequest request) {
        var consentedAt = projectService.consentPrivacy(user.id(), projectId, request.agreed());
        return new PrivacyConsentResponse(projectId, consentedAt);
    }

    @Operation(summary = "심사 제출",
            description = "필수 작성 항목이 모두 채워진 DRAFT 프로젝트를 PENDING_REVIEW로 전환한다(PROJECT_NOT_SUBMITTABLE).")
    @PostMapping("/{projectId}/submit")
    public ProjectStatusResponse submit(@LoginUser CurrentUser user, @PathVariable UUID projectId) {
        Project project = projectService.submit(user.id(), projectId);
        return new ProjectStatusResponse(project.getPublicId(), project.getStatus().name());
    }

    @Operation(summary = "스토리(소개) 수정", description = "제목/커버이미지/본문 콘텐츠 블록을 수정한다.")
    @PatchMapping("/{projectId}/story")
    public ProjectStoryResponse updateStory(
            @LoginUser CurrentUser user, @PathVariable UUID projectId,
            @Valid @RequestBody ProjectStoryRequest request) {
        Project project = projectService.updateStory(user.id(), projectId, new ProjectService.UpdateStoryCommand(
                request.title(), request.coverImageUrl(), toIntroContent(request.introContent())));
        return new ProjectStoryResponse(project.getPublicId(), project.getUpdatedAt());
    }

    @Operation(summary = "판매자 미리보기 조회",
            description = "공개 여부와 무관하게 본인 프로젝트의 상세를 미리보기로 조회한다.")
    @GetMapping("/{projectId}/preview")
    public ProjectDetailResponse preview(@LoginUser CurrentUser user, @PathVariable UUID projectId) {
        return toDetailResponse(projectQueryService.getPreview(user.id(), projectId));
    }

    @Operation(summary = "공개 상세 조회",
            description = "소비자용 프로젝트 상세. 비공개(DRAFT/PENDING_REVIEW) 프로젝트는 존재 여부를 비노출하기 위해 다른 코드가 아닌 404로 응답한다.")
    @GetMapping("/{projectId}")
    public ProjectDetailResponse getPublicDetail(@PathVariable UUID projectId) {
        return toDetailResponse(projectQueryService.getPublicDetail(projectId));
    }

    @Operation(summary = "환불 정책 조회", description = "공통 환불정책과 리워드별 간편환불 불가 여부를 함께 조회한다.")
    @GetMapping("/{projectId}/refund-policy")
    public RefundPolicyResponse getRefundPolicy(@PathVariable UUID projectId) {
        var view = projectQueryService.getRefundPolicy(projectId);
        return new RefundPolicyResponse(
                new CommonRefundPolicyResponse(view.commonPolicy().simpleRefundDeadline(), view.commonPolicy().goalFailedAutoRefund()),
                view.rewardPolicies().stream()
                        .map(r -> new RewardRefundPolicyResponse(r.rewardId(), r.simpleRefundDisabled()))
                        .toList());
    }

    @Operation(summary = "펀딩 현황 조회(판매자)",
            description = "현재 모금액/달성률/참여자수 등 판매자 대시보드용 통계를 조회한다. order-service 조회 결과 기반.")
    @GetMapping("/{projectId}/funding-status")
    public FundingStatusResponse getFundingStatus(@LoginUser CurrentUser user, @PathVariable UUID projectId) {
        var view = projectStatsService.getFundingStatus(user.id(), projectId);
        var rewardStats = view.rewardStats().stream()
                .map(r -> new RewardStatResponse(r.rewardId(), r.purchasedQuantity()))
                .toList();
        return new FundingStatusResponse(view.currentAmount(), view.achievementRate(), view.participantCount(),
                view.openNotifyCount(), view.wishCount(), rewardStats, view.remainingDays(), view.lastSyncedAt());
    }

    @Operation(summary = "찜/오픈알림 통계 조회(판매자)", description = "찜 수와 오픈예정 알림 신청 수를 조회한다.")
    @GetMapping("/{projectId}/wish-stats")
    public WishStatsResponse getWishStats(@LoginUser CurrentUser user, @PathVariable UUID projectId) {
        var view = projectStatsService.getWishStats(user.id(), projectId);
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
