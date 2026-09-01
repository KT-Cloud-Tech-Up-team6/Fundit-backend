package com.fundit.project.presentation.dto.common;

public record MessageResponse(String message) {

    private static final String DEFAULT_MESSAGE = "정상 처리되었습니다.";

    public static MessageResponse ok() {
        return new MessageResponse(DEFAULT_MESSAGE);
    }
}
