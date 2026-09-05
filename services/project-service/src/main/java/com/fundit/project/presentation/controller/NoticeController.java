package com.fundit.project.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.notice.NoticeService;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeCommentJpaEntity;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaEntity;
import com.fundit.project.infrastructure.security.CurrentMember;
import com.fundit.project.presentation.dto.NoticeCommentCreateRequest;
import com.fundit.project.presentation.dto.NoticeCommentListItemResponse;
import com.fundit.project.presentation.dto.NoticeCommentResponse;
import com.fundit.project.presentation.dto.NoticeCreateRequest;
import com.fundit.project.presentation.dto.NoticeResponse;
import com.fundit.project.presentation.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

/** PROJECT-010, PROJECT-022, PROJECT-023 — 새소식 등록/조회, 댓글 등록/조회. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NoticeController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NoticeService noticeService;

    @PostMapping("/projects/{projectId}/notices")
    public ResponseEntity<NoticeResponse> create(
            @CurrentMember UUID sellerId, @PathVariable UUID projectId,
            @Valid @RequestBody NoticeCreateRequest request) {
        ProjectNoticeJpaEntity notice = noticeService.create(sellerId, projectId,
                request.noticeType(), request.title(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(notice));
    }

    @GetMapping("/projects/{projectId}/notices")
    public PageResponse<NoticeResponse> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String noticeType,
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePaging(page, size);
        validateSort(sort);
        var result = noticeService.list(projectId, noticeType, PageRequest.of(page, size)).map(this::toResponse);
        return PageResponse.from(result);
    }

    @PostMapping("/notices/{noticeId}/comments")
    public ResponseEntity<NoticeCommentResponse> createComment(
            @CurrentMember UUID memberId, @PathVariable Long noticeId,
            @Valid @RequestBody NoticeCommentCreateRequest request) {
        ProjectNoticeCommentJpaEntity comment = noticeService.createComment(memberId, noticeId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new NoticeCommentResponse(comment.getId(), comment.getNoticeId(), comment.getContent(), comment.getCreatedAt()));
    }

    @GetMapping("/notices/{noticeId}/comments")
    public PageResponse<NoticeCommentListItemResponse> listComments(
            @PathVariable Long noticeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validatePaging(page, size);
        var result = noticeService.listComments(noticeId, PageRequest.of(page, size))
                .map(c -> new NoticeCommentListItemResponse(c.getId(), c.getContent(), c.getCreatedAt()));
        return PageResponse.from(result);
    }

    private NoticeResponse toResponse(ProjectNoticeJpaEntity notice) {
        return new NoticeResponse(notice.getId(), notice.getNoticeType(), notice.getTitle(), notice.getCreatedAt());
    }

    private void validatePaging(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "page는 0 이상, size는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다.");
        }
    }

    private void validateSort(String sort) {
        if (!"LATEST".equals(sort) && !"POPULAR".equals(sort)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "sort는 LATEST 또는 POPULAR여야 합니다.");
        }
    }
}
