package com.fundit.project.domain.aifundingstory;

public record FundingStoryFailedSlot(
        String slotId, String stage, FundingStoryRunError error) {
}
