package com.fundit.project.presentation.controller;

import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.project.application.ai.FundingStoryAiClient.ChatEventStream;
import com.fundit.project.application.ai.FundingStoryAiContracts.ChatAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.ConfirmResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.LatestSessionResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.MessageRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicRunResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.PublicSessionCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.RunAcceptedResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.SessionResponse;
import com.fundit.project.application.ai.FundingStoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

/** FE → BE 공개 Funding Story AI 계약. BE가 같은 경로로 AI를 호출한다. */
@Tag(name = "funding-story-ai")
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class FundingStoryController {

    private static final String PROJECT_HEADER = "X-Project-Id";

    private final FundingStoryService fundingStoryService;

    @Operation(summary = "Funding Story 정보 수집 세션 생성")
    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> createSession(
            @LoginUser CurrentUser user,
            @RequestHeader(PROJECT_HEADER) UUID projectId,
            @RequestBody(required = false) PublicSessionCreateRequest ignored) {
        return ResponseEntity.status(201).body(fundingStoryService.createSession(user.id(), projectId));
    }

    @Operation(summary = "프로젝트의 유효한 최신 세션 복구")
    @GetMapping("/sessions/latest")
    public LatestSessionResponse getLatestSession(
            @LoginUser CurrentUser user,
            @RequestHeader(PROJECT_HEADER) UUID projectId) {
        return fundingStoryService.getLatestSession(user.id(), projectId);
    }

    @Operation(summary = "Funding Story 세션 조회")
    @GetMapping("/sessions/{sessionId}")
    public SessionResponse getSession(
            @LoginUser CurrentUser user,
            @RequestHeader(PROJECT_HEADER) UUID projectId,
            @PathVariable UUID sessionId) {
        return fundingStoryService.getSession(user.id(), projectId, sessionId);
    }

    @Operation(summary = "첫 AI 질문 생성 시작")
    @PostMapping("/sessions/{sessionId}/start")
    public ResponseEntity<ChatAcceptedResponse> startSession(
            @LoginUser CurrentUser user,
            @RequestHeader(PROJECT_HEADER) UUID projectId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.accepted()
                .body(fundingStoryService.startSession(user.id(), projectId, sessionId));
    }

    @Operation(summary = "사용자 메시지 전달")
    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ChatAcceptedResponse> addMessage(
            @LoginUser CurrentUser user,
            @RequestHeader(PROJECT_HEADER) UUID projectId,
            @PathVariable UUID sessionId,
            @RequestBody MessageRequest request) {
        return ResponseEntity.accepted()
                .body(fundingStoryService.addMessage(user.id(), projectId, sessionId, request));
    }

    @Operation(summary = "AI 채팅 SSE 중계")
    @GetMapping(value = "/chats/{chatId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> chatEvents(
            @LoginUser CurrentUser user,
            @RequestHeader(PROJECT_HEADER) UUID projectId,
            @PathVariable UUID chatId) {
        ChatEventStream stream = fundingStoryService.openChatEvents(user.id(), projectId, chatId);
        StreamingResponseBody body = output -> {
            try (stream) {
                stream.body().transferTo(output);
                output.flush();
            }
        };
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(body);
    }

    @Operation(summary = "요약 revision 확인")
    @PostMapping("/sessions/{sessionId}/confirm")
    public ConfirmResponse confirmSession(
            @LoginUser CurrentUser user,
            @RequestHeader(PROJECT_HEADER) UUID projectId,
            @PathVariable UUID sessionId,
            @RequestBody ConfirmRequest request) {
        return fundingStoryService.confirmSession(user.id(), projectId, sessionId, request);
    }

    @Operation(summary = "Funding Story 전체 생성")
    @PostMapping("/runs")
    public ResponseEntity<RunAcceptedResponse> createRun(
            @LoginUser CurrentUser user,
            @RequestHeader(PROJECT_HEADER) UUID projectId,
            @RequestBody PublicRunCreateRequest request) {
        return ResponseEntity.accepted()
                .body(fundingStoryService.createRun(user.id(), projectId, request));
    }

    @Operation(summary = "BE가 소유한 생성 상태·결과 조회")
    @GetMapping("/runs/{runId}")
    public PublicRunResponse getRun(
            @LoginUser CurrentUser user,
            @RequestHeader(PROJECT_HEADER) UUID projectId,
            @PathVariable UUID runId) {
        return fundingStoryService.getRun(user.id(), projectId, runId);
    }
}
