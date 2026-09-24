package com.fundit.project.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.project.application.liveverification.LiveVerificationService;
import com.fundit.project.infrastructure.persistence.liveverification.LiveVerificationJpaEntity;
import com.fundit.project.presentation.dto.LiveQuestionListItemResponse;
import com.fundit.project.presentation.dto.LiveQuestionListResponse;
import com.fundit.project.presentation.dto.LiveVerificationCreateRequest;
import com.fundit.project.presentation.dto.LiveVerificationListItemResponse;
import com.fundit.project.presentation.dto.LiveVerificationListResponse;
import com.fundit.project.presentation.dto.LiveVerificationResponse;
import com.fundit.project.presentation.dto.LiveVerificationUpdateRequest;
import com.fundit.project.presentation.dto.LiveVerificationUpdateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "live-verification")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LiveVerificationController {

    private final LiveVerificationService liveVerificationService;

    @Operation(summary = "LIVE검증 콘텐츠 등록",
            description = "방송이 끝난 뒤 남는 LIVE검증 질문요약/답변을 등록한다. 방송 송출 자체는 live-service 소관. "
                    + "질문 문구·건수는 live-service 이벤트로만 채워지므로, 수신되지 않은 questionSummaryId는 404다.")
    @ApiResponse(responseCode = "201", description = "생성됨")
    @ApiResponse(responseCode = "404", description = "수신된 적 없는 questionSummaryId")
    @ApiResponse(responseCode = "409", description = "이미 답변을 등록한 질문")
    @PostMapping("/projects/{projectId}/live-verifications")
    public ResponseEntity<LiveVerificationResponse> create(
            @LoginUser CurrentUser user, @PathVariable UUID projectId,
            @Valid @RequestBody LiveVerificationCreateRequest request) {
        LiveVerificationJpaEntity entity = liveVerificationService.create(
                user.id(), projectId, request.questionSummaryId(), request.answer());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new LiveVerificationResponse(entity.getId(), entity.getAnswer(), entity.getCreatedAt()));
    }

    @Operation(summary = "LIVE검증 답변 수정")
    @PatchMapping("/live-verifications/{id}")
    public LiveVerificationUpdateResponse update(
            @LoginUser CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody LiveVerificationUpdateRequest request) {
        LiveVerificationJpaEntity entity = liveVerificationService.update(user.id(), id, request.answer());
        return new LiveVerificationUpdateResponse(entity.getId(), entity.getAnswer(), entity.getUpdatedAt());
    }

    @Operation(summary = "LIVE검증 콘텐츠 삭제")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    @DeleteMapping("/live-verifications/{id}")
    public ResponseEntity<Void> delete(@LoginUser CurrentUser user, @PathVariable Long id) {
        liveVerificationService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "LIVE검증 콘텐츠 목록 조회(소비자)",
            description = "답변이 등록된 질문만 내려간다. 질문 문구·건수는 live-service 질문요약에서 조인해 채운다.")
    @GetMapping("/projects/{projectId}/live-verifications")
    public LiveVerificationListResponse listForConsumer(@PathVariable UUID projectId) {
        var content = liveVerificationService.listForConsumer(projectId).stream()
                .map(LiveVerificationListItemResponse::from)
                .toList();
        return new LiveVerificationListResponse(content);
    }

    @Operation(summary = "LIVE 질문 목록 조회(판매자)",
            description = "live-service가 보내온 대표 질문 목록. 아직 답변하지 않은 질문도 포함해 무엇을 등록할 수 있는지 보여준다.")
    @GetMapping("/projects/{projectId}/live-questions")
    public LiveQuestionListResponse listQuestionsForSeller(@LoginUser CurrentUser user, @PathVariable UUID projectId) {
        var content = liveVerificationService.listQuestionsForSeller(user.id(), projectId).stream()
                .map(LiveQuestionListItemResponse::from)
                .toList();
        return new LiveQuestionListResponse(content);
    }
}
