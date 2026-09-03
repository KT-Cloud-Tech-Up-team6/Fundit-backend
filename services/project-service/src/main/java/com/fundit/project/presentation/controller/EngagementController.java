package com.fundit.project.presentation.controller;

import com.fundit.project.application.engagement.EngagementService;
import com.fundit.project.presentation.dto.common.MessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 팔로우·오픈알림신청. 둘 다 중복 요청을 200으로 흘려보내는 idempotent 엔드포인트다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}")
public class EngagementController {

    private final EngagementService engagementService;

    @PostMapping("/follow")
    public MessageResponse follow(@PathVariable UUID projectId) {
        engagementService.follow(projectId);
        return MessageResponse.ok();
    }

    @DeleteMapping("/follow")
    public MessageResponse unfollow(@PathVariable UUID projectId) {
        engagementService.unfollow(projectId);
        return MessageResponse.ok();
    }

    @PostMapping("/notify")
    public MessageResponse requestOpenNotify(@PathVariable UUID projectId) {
        engagementService.requestOpenNotify(projectId);
        return MessageResponse.ok();
    }

    @DeleteMapping("/notify")
    public MessageResponse cancelOpenNotify(@PathVariable UUID projectId) {
        engagementService.cancelOpenNotify(projectId);
        return MessageResponse.ok();
    }
}
