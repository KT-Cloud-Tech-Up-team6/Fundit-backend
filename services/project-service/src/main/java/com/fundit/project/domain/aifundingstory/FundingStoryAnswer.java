package com.fundit.project.domain.aifundingstory;

/** 판매자가 AI 추가 질문에 답한 내역(정보입력/생성요청 요청 바디의 answers 항목). */
public record FundingStoryAnswer(String questionId, String answer) {
}
