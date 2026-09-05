package com.fundit.project.presentation.dto;

import java.util.UUID;

public record ProjectDetailResponse(
        UUID projectId, String title, String status, Long goalAmount,
        FundingStatusSummaryResponse fundingStatus, boolean hasLiveVerification, SellerSummaryResponse seller) {
}
