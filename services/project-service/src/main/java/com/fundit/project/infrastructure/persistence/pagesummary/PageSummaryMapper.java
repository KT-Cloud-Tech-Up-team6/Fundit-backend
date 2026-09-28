package com.fundit.project.infrastructure.persistence.pagesummary;

import com.fundit.project.domain.pagesummary.PageSummary;
import com.fundit.project.domain.pagesummary.PageSummaryStatus;
import org.springframework.stereotype.Component;

@Component
class PageSummaryMapper {

    PageSummary toDomain(PageSummaryJpaEntity entity) {
        return PageSummary.builder()
                .projectId(entity.getProjectId())
                .dirtyAt(entity.getDirtyAt())
                .handledDirtyAt(entity.getHandledDirtyAt())
                .contentHash(entity.getContentHash())
                .sourceRevision(entity.getSourceRevision())
                .attempt(entity.getAttempt())
                .runId(entity.getRunId())
                .status(entity.getStatus() == null ? null : PageSummaryStatus.valueOf(entity.getStatus()))
                .sections(entity.getSections())
                .errorCode(entity.getErrorCode())
                .retryCount(entity.getRetryCount())
                .nextAttemptAt(entity.getNextAttemptAt())
                .requestedAt(entity.getRequestedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }

    PageSummaryJpaEntity toEntity(PageSummary domain) {
        return PageSummaryJpaEntity.builder()
                .projectId(domain.getProjectId())
                .dirtyAt(domain.getDirtyAt())
                .handledDirtyAt(domain.getHandledDirtyAt())
                .contentHash(domain.getContentHash())
                .sourceRevision(domain.getSourceRevision())
                .attempt(domain.getAttempt())
                .runId(domain.getRunId())
                .status(domain.getStatus() == null ? null : domain.getStatus().name())
                .sections(domain.getSections())
                .errorCode(domain.getErrorCode())
                .retryCount(domain.getRetryCount())
                .nextAttemptAt(domain.getNextAttemptAt())
                .requestedAt(domain.getRequestedAt())
                .completedAt(domain.getCompletedAt())
                .build();
    }
}
