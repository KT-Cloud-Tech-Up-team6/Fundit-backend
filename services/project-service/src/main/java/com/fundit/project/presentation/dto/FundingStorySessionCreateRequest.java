package com.fundit.project.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record FundingStorySessionCreateRequest(
        @NotBlank String productDescription,
        List<String> productImageUrls,
        @Valid List<FundingStoryAnswerRequest> answers
) {
}
