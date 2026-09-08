package com.fundit.project.presentation.controller;

import com.fundit.project.application.project.ProjectReviewService;
import com.fundit.project.application.project.ReviewDecision;
import com.fundit.project.domain.project.Project;
import com.fundit.project.infrastructure.security.CurrentAdmin;
import com.fundit.project.presentation.dto.ProjectStatusResponse;
import com.fundit.project.presentation.dto.ReviewDecisionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** PROJECT-030 — 관리자 전용 심사 승인/반려. */
@RestController
@RequestMapping("/api/v1/admin/projects")
@RequiredArgsConstructor
public class AdminProjectController {

    private final ProjectReviewService projectReviewService;

    @PostMapping("/{projectId}/review-decision")
    public ProjectStatusResponse reviewDecision(
            @CurrentAdmin UUID adminId, @PathVariable UUID projectId,
            @Valid @RequestBody ReviewDecisionRequest request) {
        Project project = projectReviewService.decide(adminId, projectId,
                ReviewDecision.valueOf(request.decision()), request.rejectReason());
        return new ProjectStatusResponse(project.getPublicId(), project.getStatus().name());
    }
}
