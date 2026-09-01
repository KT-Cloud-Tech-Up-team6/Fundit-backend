package com.fundit.project.domain.project;

import java.time.Instant;
import java.util.UUID;

public interface ProjectReviewRequestRepository {

    void submit(Long projectId, Instant submittedAt);

    /** 해당 프로젝트의 가장 최근 검수 요청 행을 승인/반려 결과로 갱신한다. */
    void resolveLatest(Long projectId,
                       ReviewRequestStatus status,
                       String rejectReason,
                       UUID reviewerId,
                       Instant reviewedAt);
}
