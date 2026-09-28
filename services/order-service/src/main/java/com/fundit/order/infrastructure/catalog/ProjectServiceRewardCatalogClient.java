package com.fundit.order.infrastructure.catalog;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.order.application.catalog.RewardCatalogClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectServiceRewardCatalogClient implements RewardCatalogClient {

    private final RestClient projectServiceRestClient;

    @Override
    public List<RewardSnapshot> getRewards(UUID projectId) {
        try {
            ProjectRewardResponse[] response = projectServiceRestClient.get()
                    .uri("/api/v1/projects/{projectId}/rewards", projectId)
                    .retrieve()
                    .body(ProjectRewardResponse[].class);
            if (response == null) {
                return List.of();
            }
            return Arrays.stream(response).map(this::toSnapshot).toList();
        } catch (RestClientException e) {
            throw new DependencyFailureException(e);
        }
    }

    private RewardSnapshot toSnapshot(ProjectRewardResponse r) {
        List<OptionGroupSnapshot> groups = r.options() == null ? List.of() : r.options().stream()
                .map(g -> new OptionGroupSnapshot(g.groupId(), g.groupName(),
                        g.values() == null ? List.of() : g.values().stream()
                                .map(v -> new OptionValueSnapshot(v.valueId(), v.value()))
                                .toList()))
                .toList();
        // 얼리버드 리워드는 할인가로 청구한다(PM 결정 09-28) — 화면의 리워드 카드·주문서와 결제 금액을 맞춘다.
        // project-service가 계산해 내려주는 값을 그대로 쓴다(할인 계산을 여기서 다시 하지 않는다).
        long unitPrice = r.isEarlyBird() && r.earlyBirdDiscountedPrice() != null ? r.earlyBirdDiscountedPrice() : r.price();
        return new RewardSnapshot(r.rewardId(), r.name(), unitPrice, r.isLimited(), groups);
    }

    // ProjectDomainApiSpec.md #14 응답 형태 그대로 — order-service는 이 중 name/price(얼리버드면 할인가)/options만 쓴다.
    private record ProjectRewardResponse(Long rewardId, String rewardDisplayCode, String name, long price,
                                          boolean isEarlyBird, Long earlyBirdDiscountedPrice, boolean isLimited,
                                          Integer remainingStock, List<OptionGroupResponse> options, boolean soldOut) {
    }

    private record OptionGroupResponse(Long groupId, String groupName, List<OptionValueResponse> values) {
    }

    private record OptionValueResponse(Long valueId, String value) {
    }
}
