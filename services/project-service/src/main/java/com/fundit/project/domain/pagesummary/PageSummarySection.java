package com.fundit.project.domain.pagesummary;

/** AI 요약 한 절. {@code role}은 WHAT(무엇을)·WHY(왜). */
public record PageSummarySection(String role, String headline, String description) {
}
