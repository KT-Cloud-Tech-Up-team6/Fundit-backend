package com.fundit.project.domain.aifundingstory;

/** 판매자 입력에 근거 없는 항목을 식별해 표시하는 경고(PROJECT-012). */
public record FundingStoryWarning(String field, String reason) {
}
