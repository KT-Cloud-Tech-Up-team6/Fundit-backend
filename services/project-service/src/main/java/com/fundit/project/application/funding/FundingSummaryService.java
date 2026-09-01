package com.fundit.project.application.funding;

import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.application.port.MemberPort;
import com.fundit.project.application.project.ProjectAccessGuard;
import com.fundit.project.domain.project.Project;
import com.fundit.project.infrastructure.persistence.engagement.OpenNotifyRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PROJECT-027. 판매자용 펀딩 현황.
 * 집계값은 구매자 상세조회와 같은 5분 캐시(FundingStatsReader)를 거친다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FundingSummaryService {

    private final ProjectAccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;
    private final FundingStatsReader fundingStatsReader;
    private final MemberPort memberPort;
    private final OpenNotifyRequestJpaRepository openNotifyJpaRepository;

    public FundingSummary find(UUID projectId) {
        var currentUser = currentUserProvider.require();
        Project project = accessGuard.findOwned(projectId, currentUser);

        FundingPort.FundingStats stats = fundingStatsReader.read(project.getId());
        long wishCount = memberPort.countWishes(project.getId());
        long openNotifyCount = openNotifyJpaRepository.countByProjectId(project.getId());

        return new FundingSummary(project, stats, wishCount, openNotifyCount);
    }

    public record FundingSummary(Project project,
                                 FundingPort.FundingStats stats,
                                 long wishCount,
                                 long openNotifyCount) {
    }
}
