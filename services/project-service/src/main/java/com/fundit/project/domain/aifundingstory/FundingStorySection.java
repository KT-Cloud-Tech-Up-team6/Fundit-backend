package com.fundit.project.domain.aifundingstory;

import java.util.List;

/** 생성된 상세페이지 초안의 섹션 하나(INTRO/FEATURE 등). */
public record FundingStorySection(String type, String title, String body, List<String> images) {
}
