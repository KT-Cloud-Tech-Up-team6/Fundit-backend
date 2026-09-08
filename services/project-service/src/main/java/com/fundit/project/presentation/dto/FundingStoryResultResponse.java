package com.fundit.project.presentation.dto;

import java.util.List;

public record FundingStoryResultResponse(
        List<FundingStorySectionResponse> sections,
        List<FundingStoryImageSourceResponse> imagesSource,
        List<FundingStoryWarningResponse> warnings
) {
}
