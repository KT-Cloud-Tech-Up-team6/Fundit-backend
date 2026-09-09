package com.fundit.order.presentation.controller;

import com.fundit.order.application.restock.RestockNotifyService;
import com.fundit.order.infrastructure.security.CurrentMember;
import com.fundit.order.presentation.dto.RestockNotifyRequest;
import com.fundit.order.presentation.dto.RestockNotifyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RestockNotifyController {

    private final RestockNotifyService restockNotifyService;

    @PostMapping("/api/v1/reward-restock-notifications")
    public RestockNotifyResponse request(@CurrentMember UUID memberId, @Valid @RequestBody RestockNotifyRequest request) {
        restockNotifyService.request(memberId, request.rewardId());
        return new RestockNotifyResponse(request.rewardId(), true);
    }
}
