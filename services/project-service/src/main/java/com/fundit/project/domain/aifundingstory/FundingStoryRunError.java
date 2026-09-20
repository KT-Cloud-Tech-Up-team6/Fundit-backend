package com.fundit.project.domain.aifundingstory;

public record FundingStoryRunError(
        String code, String message, boolean retryable, Object detail) {
}
