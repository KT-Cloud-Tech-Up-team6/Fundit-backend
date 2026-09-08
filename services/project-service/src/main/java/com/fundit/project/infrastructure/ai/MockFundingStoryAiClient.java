package com.fundit.project.infrastructure.ai;

import com.fundit.project.application.ai.FundingStoryAiClient;
import com.fundit.project.domain.aifundingstory.FundingStoryAnswer;
import com.fundit.project.domain.aifundingstory.FundingStoryImageSource;
import com.fundit.project.domain.aifundingstory.FundingStoryResult;
import com.fundit.project.domain.aifundingstory.FundingStorySection;
import com.fundit.project.domain.aifundingstory.FundingStoryWarning;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link FundingStoryAiClient} 클래스 주석 참고 — 실제 외부 AI 연동 전 placeholder.
 * 판매자가 입력한 제품 설명을 그대로 INTRO 섹션 본문에 반영하고, 업로드한 이미지가 있으면
 * imagesSource에 UPLOADED로 표시한다. 근거 없는 주장 탐지 등 실제 AI 판단 로직은 없으므로
 * warnings는 항상 비워둔다.
 */
@Component
public class MockFundingStoryAiClient implements FundingStoryAiClient {

    @Override
    public FundingStoryResult generate(String productDescription, List<String> productImageUrls, List<FundingStoryAnswer> answers) {
        List<String> images = productImageUrls == null ? List.of() : productImageUrls;

        FundingStorySection intro = new FundingStorySection("INTRO", "제품 소개", productDescription, images);
        List<FundingStoryImageSource> imagesSource = images.stream()
                .map(url -> new FundingStoryImageSource(url, "UPLOADED"))
                .toList();

        return new FundingStoryResult(List.of(intro), imagesSource, List.of());
    }
}
