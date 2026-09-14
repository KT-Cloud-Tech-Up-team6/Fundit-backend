package com.fundit.notification.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.notification.application.live.LiveNotifyService;
import com.fundit.notification.presentation.dto.LiveNotifyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** LIVE 시작 알림 신청/해제(NOTI-002). 둘 다 idempotent — 이미 신청/해제된 상태의 재요청도 같은 결과다. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LiveNotifyController {

    private final LiveNotifyService liveNotifyService;

    @PutMapping("/lives/{liveId}/notify")
    public LiveNotifyResponse requestNotify(@LoginUser CurrentUser user, @PathVariable UUID liveId) {
        liveNotifyService.requestNotify(liveId, user.id());
        return new LiveNotifyResponse(true);
    }

    @DeleteMapping("/lives/{liveId}/notify")
    public LiveNotifyResponse cancelNotify(@LoginUser CurrentUser user, @PathVariable UUID liveId) {
        liveNotifyService.cancelNotify(liveId, user.id());
        return new LiveNotifyResponse(false);
    }
}
