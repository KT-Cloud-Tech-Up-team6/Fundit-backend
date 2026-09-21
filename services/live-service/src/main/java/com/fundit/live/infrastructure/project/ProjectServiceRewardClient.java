package com.fundit.live.infrastructure.project;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.project.ProjectRewardClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * project-service 소비자용 리워드 조회({@code GET /api/v1/projects/{projectId}/rewards})를
 * AI {@code prepare} 입력 모양으로 옮긴다. 이 엔드포인트엔 총수량이 없고 잔여재고
 * ({@code remainingStock})만 있어 {@code RewardInfo.quantity}엔 잔여값을 대신 싣는다 —
 * 판매자용 관리 API로 총수량을 따로 받아오면서까지 정확히 맞출 정도로 중요한 값이 아니다(YAGNI).
 */
@Component
@RequiredArgsConstructor
public class ProjectServiceRewardClient implements ProjectRewardClient {

    private final RestClient projectServiceRestClient;

    @Override
    public List<AiClient.RewardInfo> findRewards(UUID projectId) {
        try {
            RewardDto[] rewards = projectServiceRestClient.get()
                    .uri("/api/v1/projects/{projectId}/rewards", projectId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> { })
                    .body(RewardDto[].class);
            if (rewards == null) {
                return List.of();
            }
            return Arrays.stream(rewards).map(this::toRewardInfo).toList();
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private AiClient.RewardInfo toRewardInfo(RewardDto r) {
        List<AiClient.OptionGroup> optionGroups = r.options() == null ? List.of()
                : r.options().stream()
                        .map(g -> new AiClient.OptionGroup(g.groupName(),
                                g.values() == null ? List.of() : g.values().stream().map(OptionValueDto::value).toList()))
                        .toList();
        return new AiClient.RewardInfo(r.rewardDisplayCode(), r.name(), r.description(), r.price(),
                r.isLimited(), r.remainingStock(), r.isEarlyBird(), optionGroups);
    }

    private record RewardDto(String rewardDisplayCode, String name, String description, long price,
                             boolean isLimited, Integer remainingStock, boolean isEarlyBird,
                             List<OptionGroupDto> options) {
    }

    private record OptionGroupDto(String groupName, List<OptionValueDto> values) {
    }

    private record OptionValueDto(String value) {
    }
}
