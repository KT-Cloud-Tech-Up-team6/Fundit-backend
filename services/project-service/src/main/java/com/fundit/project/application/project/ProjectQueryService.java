package com.fundit.project.application.project;

import com.fundit.project.application.funding.FundingStatsReader;
import com.fundit.project.application.port.CurrentUserProvider;
import com.fundit.project.application.port.FundingPort;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectQueryService {

    private final ProjectRepository projectRepository;
    private final RewardRepository rewardRepository;
    private final ProjectAccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;
    private final FundingStatsReader fundingStatsReader;

    /** PROJECT-002. 다른 판매자의 프로젝트가 섞이지 않도록 쿼리 자체에 seller_id를 강제한다. */
    public SellerProjects listForSeller(String status, int page, int size) {
        var currentUser = currentUserProvider.require();
        List<ProjectStatus> statuses = ProjectListFilter.resolve(status);

        List<Project> projects = projectRepository.findBySeller(currentUser.id(), statuses, page, size);
        long total = projectRepository.countBySeller(currentUser.id(), statuses);
        return new SellerProjects(projects, page, size, total);
    }

    /** PROJECT-003. 비공개 프로젝트는 소유자가 아니면 404로 존재를 숨긴다. */
    public ProjectDetail findDetail(UUID projectId) {
        UUID viewerId = currentUserProvider.find()
                .map(CurrentUserProvider.CurrentUser::id)
                .orElse(null);
        Project project = accessGuard.findVisible(projectId, viewerId);

        FundingPort.FundingStats stats = fundingStatsReader.read(project.getId());
        boolean simpleRefundDisabled = rewardRepository.findActiveByProjectId(project.getId()).stream()
                .anyMatch(Reward::isSimpleRefundDisabled);

        return new ProjectDetail(project, stats, simpleRefundDisabled);
    }

    public record SellerProjects(List<Project> projects, int page, int size, long totalElements) {
    }

    /** simpleRefundDisabled는 리워드 중 하나라도 true면 true인 참고용 요약값이다. */
    public record ProjectDetail(Project project, FundingPort.FundingStats stats, boolean simpleRefundDisabled) {
    }
}
