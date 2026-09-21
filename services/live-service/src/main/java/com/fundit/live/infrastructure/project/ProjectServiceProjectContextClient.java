package com.fundit.live.infrastructure.project;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.project.ProjectContextClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * project-service 공개 상세 API({@code GET /api/v1/projects/{projectId}})에서 AI 컨텍스트에
 * 필요한 필드만 뽑아온다. {@code projectDisplayCode}는 이 응답에 없어(판매자용 목록 API 전용
 * 필드) AI 쪽엔 null로 보낸다(협의 확정 전까지 없는 값을 지어내지 않는다).
 */
@Component
@RequiredArgsConstructor
public class ProjectServiceProjectContextClient implements ProjectContextClient {

    private final RestClient projectServiceRestClient;

    @Override
    public Optional<ProjectContext> find(UUID projectId) {
        try {
            ProjectDetail detail = projectServiceRestClient.get()
                    .uri("/api/v1/projects/{projectId}", projectId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> { })
                    .body(ProjectDetail.class);
            if (detail == null) {
                return Optional.empty();
            }
            List<String> introTexts = detail.introContent() == null ? List.of()
                    : detail.introContent().stream()
                            .filter(block -> "TEXT".equals(block.type()) && block.value() != null)
                            .map(IntroBlock::value)
                            .toList();
            Integer achievementRate = detail.fundingStatus() == null ? null : detail.fundingStatus().achievementRate();
            Integer remainingDays = detail.fundingStatus() == null ? null : detail.fundingStatus().remainingDays();
            return Optional.of(new ProjectContext(detail.title(), detail.categoryMajor(), detail.categoryMinor(),
                    introTexts, achievementRate, remainingDays));
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private record ProjectDetail(String title, String categoryMajor, String categoryMinor,
                                 List<IntroBlock> introContent, FundingStatus fundingStatus) {
    }

    private record IntroBlock(String type, String value) {
    }

    private record FundingStatus(Integer achievementRate, Integer remainingDays) {
    }
}
