package com.fundit.project.presentation.dto.notice;

import com.fundit.project.application.notice.NoticeService.NoticeSummary;
import com.fundit.project.infrastructure.persistence.notice.ProjectNoticeJpaEntity;

import java.time.Instant;

public record NoticeSummaryResponse(Long noticeId,
                                    String noticeType,
                                    String title,
                                    Instant createdAt,
                                    int commentCount) {

    public static NoticeSummaryResponse from(NoticeSummary summary) {
        ProjectNoticeJpaEntity notice = summary.notice();
        return new NoticeSummaryResponse(notice.getId(), notice.getNoticeType(), notice.getTitle(),
                notice.getCreatedAt(), summary.commentCount());
    }
}
