package com.fundit.live.presentation.dto;

import com.fundit.live.application.chat.ChatTokenService.ChatToken;

import java.util.List;

public record ChatTokenResponse(String token, String roomArn, List<String> capabilities) {

    public static ChatTokenResponse from(ChatToken t) {
        return new ChatTokenResponse(t.token(), t.roomArn(), t.capabilities());
    }
}
