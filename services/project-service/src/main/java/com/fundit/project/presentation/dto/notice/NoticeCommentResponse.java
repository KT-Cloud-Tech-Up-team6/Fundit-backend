package com.fundit.project.presentation.dto.notice;

import com.fundit.project.application.notice.NoticeService.CommentWithAuthor;

import java.time.Instant;

public record NoticeCommentResponse(Long commentId,
                                    String memberNickname,
                                    String content,
                                    Instant createdAt) {

    public static NoticeCommentResponse from(CommentWithAuthor source) {
        return new NoticeCommentResponse(
                source.comment().getId(),
                source.nickname(),
                source.comment().getContent(),
                source.comment().getCreatedAt());
    }
}
