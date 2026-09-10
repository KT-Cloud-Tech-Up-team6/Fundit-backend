package com.fundit.project.presentation.controller;

import com.fundit.project.application.project.ProjectReviewService;
import com.fundit.project.application.project.ReviewDecision;
import com.fundit.project.domain.project.Project;
import com.fundit.project.infrastructure.security.CurrentAdmin;
import com.fundit.project.presentation.dto.ProjectStatusResponse;
import com.fundit.project.presentation.dto.ReviewDecisionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** PROJECT-030 — 관리자 전용 심사 승인/반려. */
@Tag(name = "project-review")
@RestController
@RequestMapping("/api/v1/admin/projects")
@RequiredArgsConstructor
public class AdminProjectController {

    private final ProjectReviewService projectReviewService;

    @Operation(summary = "프로젝트 심사 승인/반려",
            description = "관리자(role=admin)만 호출 가능. decision=APPROVE 시 funding_start_at/funding_deadline이 이 시점에 확정된다.")
    @PostMapping("/{projectId}/review-decision")
    public ProjectStatusResponse reviewDecision(
            @CurrentAdmin UUID adminId, @PathVariable UUID projectId,
            @Valid @RequestBody ReviewDecisionRequest request) {
        Project project = projectReviewService.decide(adminId, projectId,
                ReviewDecision.valueOf(request.decision()), request.rejectReason());
        return new ProjectStatusResponse(project.getPublicId(), project.getStatus().name());
    }
}
