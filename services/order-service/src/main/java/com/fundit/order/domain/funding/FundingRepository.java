package com.fundit.order.domain.funding;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundingRepository {

    Optional<Funding> findByPublicId(UUID publicId);

    Optional<Funding> findById(Long id);

    Funding save(Funding funding);

    Page<Funding> findByMemberId(UUID memberId, FundingStatus status, Pageable pageable);

    /** ORDER-013 배치 대상 조회 — payment_expires_at이 threshold 이전인 PENDING 건. */
    List<Funding> findPendingExpiredBefore(Instant threshold);

    /** ORDER-006 배치 대상 조회 — 아직 판정되지 않은(마감 전 진행 중) 해당 프로젝트의 참여 건. */
    List<Funding> findActiveByProjectId(Long projectId);
}
