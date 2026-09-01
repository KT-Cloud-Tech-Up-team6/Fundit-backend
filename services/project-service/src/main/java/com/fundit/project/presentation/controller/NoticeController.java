package com.fundit.project.presentation.controller;

import com.fundit.project.application.notice.NoticeService;
import com.fundit.project.presentation.dto.common.ListResponse;
import com.fundit.project.presentation.dto.notice.NoticeCommentCreateRequest;
import com.fundit.project.presentation.dto.notice.NoticeCommentIdResponse;
import com.fundit.project.presentation.dto.notice.NoticeCommentResponse;
import com.fundit.project.presentation.dto.notice.NoticeCreateRequest;
import com.fundit.project.presentation.dto.notice.NoticeIdResponse;
import com.fundit.project.presentation.dto.notice.NoticeSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/notices")
public class NoticeController {

    private final NoticeService noticeService;

    @PostMapping
    public ResponseEntity<NoticeIdResponse> create(@PathVariable UUID projectId,
                                                   @Valid @RequestBody NoticeCreateRequest request) {
        var notice = noticeService.create(projectId, request.noticeType(), request.title(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(new NoticeIdResponse(notice.getId()));
    }

    @GetMapping
    public ListResponse<NoticeSummaryResponse> list(@PathVariable UUID projectId,
                                                    @RequestParam(required = false) String noticeType,
                                                    @RequestParam(required = false) String sort) {
        return ListResponse.of(noticeService.list(projectId, noticeType, sort).stream()
                .map(NoticeSummaryResponse::from)
                .toList());
    }

    @PostMapping("/{noticeId}/comments")
    public ResponseEntity<NoticeCommentIdResponse> addComment(
            @PathVariable UUID projectId,
            @PathVariable Long noticeId,
            @Valid @RequestBody NoticeCommentCreateRequest request) {

        var comment = noticeService.addComment(projectId, noticeId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(new NoticeCommentIdResponse(comment.getId()));
    }

    @GetMapping("/{noticeId}/comments")
    public ListResponse<NoticeCommentResponse> listComments(@PathVariable UUID projectId,
                                                            @PathVariable Long noticeId) {
        return ListResponse.of(noticeService.listComments(projectId, noticeId).stream()
                .map(NoticeCommentResponse::from)
                .toList());
    }
}
