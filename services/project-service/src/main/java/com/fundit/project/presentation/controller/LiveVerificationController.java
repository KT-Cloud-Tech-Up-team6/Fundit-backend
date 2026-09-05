package com.fundit.project.presentation.controller;

import com.fundit.project.application.liveverification.LiveVerificationService;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaEntity;
import com.fundit.project.infrastructure.security.CurrentMember;
import com.fundit.project.presentation.dto.LiveVerificationCreateRequest;
import com.fundit.project.presentation.dto.LiveVerificationListItemResponse;
import com.fundit.project.presentation.dto.LiveVerificationListResponse;
import com.fundit.project.presentation.dto.LiveVerificationResponse;
import com.fundit.project.presentation.dto.LiveVerificationUpdateRequest;
import com.fundit.project.presentation.dto.LiveVerificationUpdateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** PROJECT-014, PROJECT-019 — LIVE검증 콘텐츠 등록/수정/삭제/조회. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LiveVerificationController {

    private final LiveVerificationService liveVerificationService;

    @PostMapping("/projects/{projectId}/live-verifications")
    public ResponseEntity<LiveVerificationResponse> create(
            @CurrentMember UUID sellerId, @PathVariable UUID projectId,
            @Valid @RequestBody LiveVerificationCreateRequest request) {
        LiveVerificationJpaEntity entity = liveVerificationService.create(
                sellerId, projectId, request.questionSummaryId(), request.answer());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LiveVerificationResponse(entity.getId(), entity.getAnswer(), entity.getCreatedAt()));
    }

    @PatchMapping("/live-verifications/{id}")
    public LiveVerificationUpdateResponse update(
            @CurrentMember UUID sellerId, @PathVariable Long id,
            @Valid @RequestBody LiveVerificationUpdateRequest request) {
        LiveVerificationJpaEntity entity = liveVerificationService.update(sellerId, id, request.answer());
        return new LiveVerificationUpdateResponse(entity.getId(), entity.getAnswer(), entity.getUpdatedAt());
    }

    @DeleteMapping("/live-verifications/{id}")
    public ResponseEntity<Void> delete(@CurrentMember UUID sellerId, @PathVariable Long id) {
        liveVerificationService.delete(sellerId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{projectId}/live-verifications")
    public LiveVerificationListResponse listForConsumer(@PathVariable UUID projectId) {
        var content = liveVerificationService.listForConsumer(projectId).stream()
                .map(e -> new LiveVerificationListItemResponse(e.getId(), e.getQuestionCount(), e.getAnswer()))
                .toList();
        return new LiveVerificationListResponse(content);
    }
}
