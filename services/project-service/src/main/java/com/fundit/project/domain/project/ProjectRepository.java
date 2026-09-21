package com.fundit.project.domain.project;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {

    Project save(Project project);

    /** 소프트 삭제된 프로젝트는 제외한다. */
    Optional<Project> findByPublicId(UUID publicId);

    /** 소프트 삭제된 프로젝트는 제외한다. */
    Optional<Project> findById(Long id);

    /**
     * FundingDeadlineWatcher 배치 대상 조회 — status=ONGOING이고 funding_deadline이 지났는데
     * 아직 통지(deadline_notified_at)하지 않은 프로젝트만 가져온다(중복 발행 방지).
     * 한 번에 몰릴 수 있는 대상 건수를 한 트랜잭션에서 전부 읽지 않도록 {@code pageable}로
     * 배치 크기를 제한한다 — 남은 건은 다음 폴링 주기에 마저 처리된다(RewardEventOutboxWorker와 동일 패턴).
     */
    List<Project> findOngoingWithDeadlineReached(Instant now, Pageable pageable);
}
