package com.fundit.project.presentation.dto;

import java.util.List;
import java.util.UUID;

public record FundingStorySessionResponse(
        UUID sessionId,
        String status,
        List<FundingStoryAdditionalQuestionResponse> additionalQuestions,
        FundingStoryResultResponse result
) {
}
