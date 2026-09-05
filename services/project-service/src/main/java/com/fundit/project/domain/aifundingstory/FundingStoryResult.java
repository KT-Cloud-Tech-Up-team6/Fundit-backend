package com.fundit.project.domain.aifundingstory;

import java.util.List;

public record FundingStoryResult(
        List<FundingStorySection> sections,
        List<FundingStoryImageSource> imagesSource,
        List<FundingStoryWarning> warnings
) {
}
