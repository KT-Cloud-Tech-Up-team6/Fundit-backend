package com.fundit.project.presentation.controller;

import com.fundit.project.application.funding.FundingSummaryService;
import com.fundit.project.presentation.dto.funding.FundingSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/funding-summary")
public class FundingSummaryController {

    private final FundingSummaryService fundingSummaryService;

    @GetMapping
    public FundingSummaryResponse find(@PathVariable UUID projectId) {
        return FundingSummaryResponse.from(fundingSummaryService.find(projectId), Instant.now());
    }
}
