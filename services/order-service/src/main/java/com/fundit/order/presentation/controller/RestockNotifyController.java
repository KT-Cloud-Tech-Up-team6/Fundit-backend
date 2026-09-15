package com.fundit.order.presentation.controller;

import com.fundit.order.application.restock.RestockNotifyService;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.order.presentation.dto.RestockNotifyRequest;
import com.fundit.order.presentation.dto.RestockNotifyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RestockNotifyController {

    private final RestockNotifyService restockNotifyService;

    @PostMapping("/api/v1/reward-restock-notifications")
    public RestockNotifyResponse request(@LoginUser CurrentUser user, @Valid @RequestBody RestockNotifyRequest request) {
        restockNotifyService.request(user.id(), request.rewardId());
        return new RestockNotifyResponse(request.rewardId(), true);
    }
}
