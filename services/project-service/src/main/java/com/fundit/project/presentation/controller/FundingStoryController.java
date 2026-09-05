package com.fundit.project.presentation.controller;

import com.fundit.project.application.ai.FundingStoryService;
import com.fundit.project.domain.aifundingstory.FundingStoryAnswer;
import com.fundit.project.domain.aifundingstory.FundingStoryResult;
import com.fundit.project.domain.project.Project;
import com.fundit.project.infrastructure.persistence.aifundingstory.AiFundingStorySessionJpaEntity;
import com.fundit.project.infrastructure.security.CurrentMember;
import com.fundit.project.presentation.dto.FundingStoryAdditionalQuestionResponse;
import com.fundit.project.presentation.dto.FundingStoryApplyRequest;
import com.fundit.project.presentation.dto.FundingStoryApplyResponse;
import com.fundit.project.presentation.dto.FundingStoryImageSourceResponse;
import com.fundit.project.presentation.dto.FundingStoryResultResponse;
import com.fundit.project.presentation.dto.FundingStorySectionResponse;
import com.fundit.project.presentation.dto.FundingStorySessionCreateRequest;
import com.fundit.project.presentation.dto.FundingStorySessionCreateResponse;
import com.fundit.project.presentation.dto.FundingStorySessionResponse;
import com.fundit.project.presentation.dto.FundingStoryWarningResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** PROJECT-011, PROJECT-012 — 펀딩스토리 AI 정보입력/생성요청, 결과조회, 결과반영. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FundingStoryController {

    private final FundingStoryService fundingStoryService;

    @PostMapping("/projects/{projectId}/ai/funding-story/sessions")
    public ResponseEntity<FundingStorySessionCreateResponse> createSession(
            @CurrentMember UUID sellerId, @PathVariable UUID projectId,
            @Valid @RequestBody FundingStorySessionCreateRequest request) {
        List<FundingStoryAnswer> answers = request.answers() == null ? null : request.answers().stream()
                .map(a -> new FundingStoryAnswer(a.questionId(), a.answer()))
                .toList();
        AiFundingStorySessionJpaEntity session = fundingStoryService.createSession(
                sellerId, projectId, request.productDescription(), request.productImageUrls(), answers);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new FundingStorySessionCreateResponse(session.getId(), session.getStatus()));
    }

    @GetMapping("/ai/funding-story/sessions/{sessionId}")
    public FundingStorySessionResponse getSession(@CurrentMember UUID sellerId, @PathVariable UUID sessionId) {
        AiFundingStorySessionJpaEntity session = fundingStoryService.getSession(sellerId, sessionId);
        return toResponse(session);
    }

    @PatchMapping("/ai/funding-story/sessions/{sessionId}/apply")
    public FundingStoryApplyResponse apply(
            @CurrentMember UUID sellerId, @PathVariable UUID sessionId,
            @Valid @RequestBody FundingStoryApplyRequest request) {
        Map<String, String> editsBySectionType = request.edits() == null ? Map.of() : request.edits().stream()
                .collect(Collectors.toMap(e -> e.sectionType(), e -> e.body(), (a, b) -> b));
        Project project = fundingStoryService.applyToProject(sellerId, sessionId, request.mode(), editsBySectionType);
        return new FundingStoryApplyResponse(project.getPublicId(), project.getUpdatedAt());
    }

    private FundingStorySessionResponse toResponse(AiFundingStorySessionJpaEntity session) {
        List<FundingStoryAdditionalQuestionResponse> additionalQuestions = session.getAdditionalQuestions() == null
                ? List.of()
                : session.getAdditionalQuestions().stream()
                        .map(q -> new FundingStoryAdditionalQuestionResponse(q.questionId(), q.question()))
                        .toList();
        FundingStoryResult result = session.getResult();
        FundingStoryResultResponse resultResponse = result == null ? null : new FundingStoryResultResponse(
                result.sections().stream().map(s -> new FundingStorySectionResponse(s.type(), s.title(), s.body(), s.images())).toList(),
                result.imagesSource().stream().map(i -> new FundingStoryImageSourceResponse(i.url(), i.source())).toList(),
                result.warnings().stream().map(w -> new FundingStoryWarningResponse(w.field(), w.reason())).toList());

        return new FundingStorySessionResponse(session.getId(), session.getStatus(), additionalQuestions, resultResponse);
    }
}
