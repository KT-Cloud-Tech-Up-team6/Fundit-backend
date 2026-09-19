package com.fundit.project.presentation.dto;

import java.util.List;
import java.util.UUID;

public record ProjectDetailResponse(
        UUID projectId, String title, String status, Long goalAmount,
        String coverImageUrl, List<IntroContentBlockResponse> introContent,
        FundingStatusSummaryResponse fundingStatus, boolean hasLiveVerification, SellerSummaryResponse seller,
        String categoryMajor, String categoryMinor) {
}
