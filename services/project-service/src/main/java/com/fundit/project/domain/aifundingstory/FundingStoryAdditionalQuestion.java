package com.fundit.project.domain.aifundingstory;

/** AI가 판매자 입력이 부족하다고 판단해 추가로 되묻는 질문. */
public record FundingStoryAdditionalQuestion(String questionId, String question) {
}
