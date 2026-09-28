package com.fundit.project.presentation.dto;

import java.util.List;
import java.util.UUID;

public record ProjectDetailResponse(
        UUID projectId, String title, String status, Long goalAmount,
        String coverImageUrl, List<IntroContentBlockResponse> introContent,
        FundingStatusSummaryResponse fundingStatus, boolean hasLiveVerification, SellerSummaryResponse seller,
        String categoryMajor, String categoryMinor, String businessType, PageSummaryResponse pageSummary) {

    /** 상세 상단 AI 요약(#169). 생성 중이면 {@code sections} 없이 GENERATING, 실패·미생성이면 필드 자체가 없다. */
    public record PageSummaryResponse(String status, List<SectionResponse> sections) {
    }

    /** {@code role}: WHAT(무엇을)·WHY(왜). */
    public record SectionResponse(String role, String headline, String description) {
    }
}
