package com.fundit.project.presentation.controller;

import com.fundit.project.application.project.ProjectCommandService;
import com.fundit.project.application.project.ProjectQueryService;
import com.fundit.project.application.project.ProjectReviewService;
import com.fundit.project.domain.project.Project;
import com.fundit.project.presentation.dto.common.MessageResponse;
import com.fundit.project.presentation.dto.common.PageResponse;
import com.fundit.project.presentation.dto.project.BasicInfoUpdateRequest;
import com.fundit.project.presentation.dto.project.DetailSavedResponse;
import com.fundit.project.presentation.dto.project.DetailUpdateRequest;
import com.fundit.project.presentation.dto.project.ProjectDetailResponse;
import com.fundit.project.presentation.dto.project.ProjectStatusResponse;
import com.fundit.project.presentation.dto.project.ProjectSummaryResponse;
import com.fundit.project.presentation.dto.project.ReviewRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectCommandService projectCommandService;
    private final ProjectQueryService projectQueryService;
    private final ProjectReviewService projectReviewService;

    @PostMapping
    public ResponseEntity<ProjectStatusResponse> create() {
        Project project = projectCommandService.create();
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectStatusResponse.from(project));
    }

    @GetMapping
    public PageResponse<ProjectSummaryResponse> listMyProjects(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        ProjectQueryService.SellerProjects result = projectQueryService.listForSeller(status, page, size);
        Instant now = Instant.now();
        return new PageResponse<>(
                result.projects().stream().map(project -> ProjectSummaryResponse.from(project, now)).toList(),
                result.page(),
                result.size(),
                result.totalElements());
    }

    @GetMapping("/{projectId}")
    public ProjectDetailResponse findDetail(@PathVariable UUID projectId) {
        return ProjectDetailResponse.from(projectQueryService.findDetail(projectId));
    }

    @PatchMapping("/{projectId}/basic-info")
    public ProjectStatusResponse updateBasicInfo(@PathVariable UUID projectId,
                                                 @Valid @RequestBody BasicInfoUpdateRequest request) {
        Project project = projectCommandService.updateBasicInfo(
                projectId,
                request.businessType(),
                request.categoryMajor(),
                request.categoryMinor(),
                request.goalAmount(),
                Boolean.TRUE.equals(request.privacyAgreed()));
        return ProjectStatusResponse.from(project);
    }

    @PatchMapping("/{projectId}/detail")
    public DetailSavedResponse updateDetail(@PathVariable UUID projectId,
                                            @Valid @RequestBody DetailUpdateRequest request) {
        Project project = projectCommandService.updateDetail(
                projectId,
                request.title(),
                request.thumbnailImageUrl(),
                request.introContent(),
                Boolean.TRUE.equals(request.draft()));
        return DetailSavedResponse.from(project);
    }

    @PatchMapping("/{projectId}/review")
    public ProjectStatusResponse review(@PathVariable UUID projectId,
                                        @Valid @RequestBody ReviewRequest request) {
        Project project = projectReviewService.review(
                projectId,
                request.decision(),
                request.rejectReason(),
                request.fundingStartAt(),
                request.fundingDeadline());
        return ProjectStatusResponse.from(project);
    }

    @DeleteMapping("/{projectId}")
    public MessageResponse delete(@PathVariable UUID projectId) {
        projectCommandService.delete(projectId);
        return MessageResponse.ok();
    }
}
