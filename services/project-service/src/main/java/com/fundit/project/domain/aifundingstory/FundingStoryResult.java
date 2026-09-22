package com.fundit.project.domain.aifundingstory;

import com.fundit.project.domain.project.IntroContentBlock;

import java.util.List;

/**
 * BE-owned terminal Funding Story result. Legacy fields remain readable so the existing JSONB column
 * can be reused without a database migration.
 */
public record FundingStoryResult(
        String status,
        String coverImageUrl,
        List<IntroContentBlock> introContent,
        List<FundingStoryFailedSlot> failedSlots,
        FundingStoryRunError error,
        List<FundingStorySection> sections,
        List<FundingStoryImageSource> imagesSource,
        List<FundingStoryWarning> warnings) {

    public FundingStoryResult(
            String status,
            String coverImageUrl,
            List<IntroContentBlock> introContent,
            List<FundingStoryFailedSlot> failedSlots,
            FundingStoryRunError error) {
        this(status, coverImageUrl, introContent, failedSlots, error, List.of(), List.of(), List.of());
    }

    /** Compatibility constructor for rows created by the retired synchronous mock flow. */
    public FundingStoryResult(
            List<FundingStorySection> sections,
            List<FundingStoryImageSource> imagesSource,
            List<FundingStoryWarning> warnings) {
        this(null, null, List.of(), List.of(), null, sections, imagesSource, warnings);
    }
}
