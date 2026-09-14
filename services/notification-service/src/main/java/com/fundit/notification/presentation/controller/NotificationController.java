package com.fundit.notification.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.notification.application.notification.NotificationService;
import com.fundit.notification.application.setting.NotificationSettingService;
import com.fundit.notification.presentation.dto.NotificationListItemResponse;
import com.fundit.notification.presentation.dto.NotificationReadResponse;
import com.fundit.notification.presentation.dto.NotificationSettingRequest;
import com.fundit.notification.presentation.dto.NotificationSettingResponse;
import com.fundit.notification.presentation.dto.PageResponse;
import com.fundit.notification.presentation.dto.UnreadCountResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 알림함(NOTI-003/004/005/007). 회원 식별자는 @LoginUser에서만 가져오고 요청으로 받지 않는다(security.md S4). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;
    private final NotificationSettingService notificationSettingService;

    @GetMapping("/notifications")
    public PageResponse<NotificationListItemResponse> getNotifications(
            @LoginUser CurrentUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
        var result = notificationService.getNotifications(user.id(), PageRequest.of(page, size))
                .map(n -> new NotificationListItemResponse(n.notificationId(), n.notifType(), n.title(),
                        n.relatedUrl(), n.readAt(), n.createdAt()));
        return PageResponse.from(result);
    }

    @PatchMapping("/notifications/{notificationId}/read")
    public NotificationReadResponse markRead(@LoginUser CurrentUser user, @PathVariable Long notificationId) {
        return new NotificationReadResponse(notificationId, notificationService.markRead(notificationId, user.id()));
    }

    @GetMapping("/notifications/unread-count")
    public UnreadCountResponse getUnreadCount(@LoginUser CurrentUser user) {
        return new UnreadCountResponse(notificationService.countUnread(user.id()));
    }

    @PutMapping("/notification-settings")
    public NotificationSettingResponse updateSetting(@LoginUser CurrentUser user,
                                                     @Valid @RequestBody NotificationSettingRequest request) {
        notificationSettingService.setEnabled(user.id(), request.notifType(), request.enabled());
        return new NotificationSettingResponse(true);
    }
}
