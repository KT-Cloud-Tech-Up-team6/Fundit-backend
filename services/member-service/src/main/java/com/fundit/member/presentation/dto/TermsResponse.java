package com.fundit.member.presentation.dto;

public record TermsResponse(String code, String title, String content, boolean required, String version) {
}
