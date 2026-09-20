package com.fundit.project.presentation.controller;

import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunCompletionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTargetsRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.UploadTargetsResponse;
import com.fundit.project.application.ai.FundingStoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** AI → BE 내부 callback. InternalGatewaySecretFilter가 두 경로를 보호한다. */
@RestController
@RequiredArgsConstructor
public class InternalFundingStoryAiController {

    private static final String PROJECT_HEADER = "X-Project-Id";

    private final FundingStoryService fundingStoryService;

    @PostMapping("/internal/ai/media/upload-targets")
    public UploadTargetsResponse createUploadTargets(
            @RequestHeader(PROJECT_HEADER) UUID projectId,
            @RequestBody UploadTargetsRequest request) {
        return fundingStoryService.createUploadTargets(projectId, request);
    }

    @PostMapping("/internal/ai/runs/{runId}/completion")
    public RunCompletionResponse completeRun(
            @RequestHeader(PROJECT_HEADER) UUID projectId,
            @PathVariable UUID runId,
            @RequestBody RunCompletionRequest request) {
        return fundingStoryService.completeRun(projectId, runId, request);
    }
}
