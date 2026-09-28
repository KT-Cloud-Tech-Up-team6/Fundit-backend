package com.fundit.project.application.pagesummary;

import com.fundit.common.error.BusinessException;
import com.fundit.project.application.ai.FundingStoryAiClient;
import com.fundit.project.application.ai.FundingStoryAiContracts.ArtifactError;
import com.fundit.project.application.ai.FundingStoryAiContracts.ArtifactView;
import com.fundit.project.application.ai.FundingStoryAiContracts.PageSummaryRunCreateRequest;
import com.fundit.project.application.ai.FundingStoryAiContracts.PageSummaryRunResponse;
import com.fundit.project.application.ai.FundingStoryAiContracts.ProjectSnapshot;
import com.fundit.project.application.ai.FundingStoryAiContracts.SummarySection;
import com.fundit.project.application.ai.FundingStoryContextFactory;
import com.fundit.project.domain.pagesummary.PageSummary;
import com.fundit.project.domain.pagesummary.PageSummaryRepository;
import com.fundit.project.domain.pagesummary.PageSummarySection;
import com.fundit.project.domain.pagesummary.PageSummaryStatus;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.reward.Reward;
import com.fundit.project.domain.reward.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 상세 페이지 AI 요약(#169). 쓰기 경로는 {@link #markDirtyIfPublic}로 표시만 남기고, AI 호출·폴링은
 * {@code PageSummaryWorker}가 커밋 뒤에 {@link #process}로 한다 — 요약 실패·지연이 프로젝트 저장을 막지 않는다.
 */
@Service
@RequiredArgsConstructor
public class PageSummaryService {

    static final String IMAGE_READ_URL_EXPIRED = "IMAGE_READ_URL_EXPIRED";
    private static final Set<String> ROLES = Set.of("WHAT", "WHY");
    private static final int MAX_HEADLINE = 120;
    private static final int MAX_DESCRIPTION = 400;

    private final PageSummaryRepository pageSummaryRepository;
    private final ProjectRepository projectRepository;
    private final RewardRepository rewardRepository;
    private final FundingStoryContextFactory contextFactory;
    private final FundingStoryAiClient aiClient;

    /** 요약 입력(제목·카테고리·리워드·본문)이 바뀌었을 수 있는 쓰기마다 호출한다. DRAFT는 대상이 아니다. */
    public void markDirtyIfPublic(Project project) {
        if (project.isPublic()) {
            pageSummaryRepository.markDirty(project.getId(), Instant.now());
        }
    }

    /**
     * 한 프로젝트를 한 단계 진행한다. AI 5xx·타임아웃은 그대로 던져 롤백하고 다음 주기에 다시 시도한다.
     * AI가 4xx로 거절하면 같은 요청을 반복해도 결과가 같아 실패로 닫는다.
     */
    @Transactional
    public void process(Long projectId) {
        PageSummary summary = pageSummaryRepository.findByProjectId(projectId).orElse(null);
        if (summary == null) {
            return;
        }
        Instant now = Instant.now();
        Project project = projectRepository.findById(projectId).filter(Project::isPublic).orElse(null);
        if (project == null) {
            summary.markChecked();
            summary.fail("PROJECT_NOT_PUBLIC", now);
        } else {
            try {
                advance(summary, project, now);
            } catch (BusinessException e) {
                summary.fail("AI_REQUEST_REJECTED", now);
            }
        }
        pageSummaryRepository.save(summary);
    }

    private void advance(PageSummary summary, Project project, Instant now) {
        if (summary.isDirty()) {
            List<Reward> rewards = rewardRepository.findByProjectId(project.getId());
            String hash = contextFactory.pageSummaryHash(project, rewards);
            if (summary.hasSameContent(hash)) {
                summary.markChecked();
                return;
            }
            summary.startRevision(hash);
            request(summary, project, rewards, now);
            return;
        }
        if (summary.getStatus() == PageSummaryStatus.RETRY_WAIT) {
            aiClient.retryPageSummaryRun(project.getPublicId(), summary.getRunId());
            summary.retried(now);
            return;
        }
        if (summary.getStatus() == PageSummaryStatus.REQUESTED) {
            poll(summary, project, now);
        }
    }

    private void request(PageSummary summary, Project project, List<Reward> rewards, Instant now) {
        ProjectSnapshot snapshot;
        try {
            snapshot = contextFactory.pageSummarySnapshot(project, rewards);
        } catch (BusinessException e) {
            summary.fail("IMAGE_UNAVAILABLE", now);
            return;
        }
        String trigger = summary.isFirstRevision() ? "PROJECT_REGISTRATION_COMPLETED" : "PROJECT_CONTENT_UPDATED";
        PageSummaryRunResponse response = aiClient.createPageSummaryRun(project.getPublicId(),
                new PageSummaryRunCreateRequest(summary.getSourceRevision(),
                        summary.nextIdempotencyKey(project.getPublicId()), trigger, snapshot));
        summary.requested(response.run_id(), now);
    }

    private void poll(PageSummary summary, Project project, Instant now) {
        // AI가 죽어 있으면 조회가 계속 실패하므로 시간 초과는 호출 전에 본다.
        if (summary.isTimedOut(now)) {
            summary.fail("TIMEOUT", now);
            return;
        }
        ArtifactView artifact = aiClient.getPageSummaryRun(project.getPublicId(), summary.getRunId()).pageSummary();
        String status = artifact == null ? null : artifact.status();
        if ("SUCCEEDED".equals(status)) {
            List<PageSummarySection> sections = validSections(artifact);
            if (sections == null) {
                summary.fail("INVALID_OUTPUT", now);
            } else {
                summary.succeed(sections, now);
            }
        } else if ("FAILED".equals(status)) {
            ArtifactError error = artifact.error();
            if (error != null && IMAGE_READ_URL_EXPIRED.equals(error.code())) {
                // 같은 revision·새 서명 URL·새 멱등키로 재접수(AI팀 합의). retry로는 URL을 바꿀 수 없다.
                request(summary, project, rewardRepository.findByProjectId(project.getId()), now);
            } else if (error != null && error.retryable() && summary.canRetry()) {
                summary.waitRetry(now);
            } else {
                summary.fail(error == null ? "FAILED" : error.code(), now);
            }
        } else if ("STALE".equals(status)) {
            summary.fail("STALE", now);
        }
    }

    /** AI 응답은 그대로 믿지 않는다(security.md S7) — WHAT·WHY 두 절과 길이 제한을 확인한다. */
    private static List<PageSummarySection> validSections(ArtifactView artifact) {
        if (artifact.output() == null || artifact.output().sections() == null
                || artifact.output().sections().size() != 2) {
            return null;
        }
        List<SummarySection> sections = artifact.output().sections();
        boolean valid = sections.stream().allMatch(s -> s != null && ROLES.contains(s.role())
                && isText(s.headline(), MAX_HEADLINE) && isText(s.description(), MAX_DESCRIPTION))
                && !sections.get(0).role().equals(sections.get(1).role());
        return valid
                ? sections.stream().map(s -> new PageSummarySection(s.role(), s.headline(), s.description())).toList()
                : null;
    }

    private static boolean isText(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }
}
