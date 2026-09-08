package com.fundit.order.application.catalog;

import java.util.List;
import java.util.Optional;

/**
 * 리워드 가격/이름/옵션은 project-service 소유라 동기 HTTP로 조회한다(project-service
 * `GET /api/v1/projects/{projectId}/rewards`, ProjectDomainApiSpec.md #14). 재고(잔여수량)는
 * order-service 자신의 inventories가 단일 진실 공급원이라 이 포트로 가져오지 않는다.
 */
public interface RewardCatalogClient {

    List<RewardSnapshot> getRewards(Long projectId);

    record RewardSnapshot(Long rewardId, String name, long price, boolean isLimited,
                           List<OptionGroupSnapshot> optionGroups) {

        public Optional<ResolvedOption> resolveOption(Long optionValueId) {
            for (OptionGroupSnapshot group : optionGroups) {
                for (OptionValueSnapshot value : group.values()) {
                    if (value.valueId().equals(optionValueId)) {
                        return Optional.of(new ResolvedOption(group.groupId(), group.groupName(),
                                value.valueId(), value.value()));
                    }
                }
            }
            return Optional.empty();
        }
    }

    record OptionGroupSnapshot(Long groupId, String groupName, List<OptionValueSnapshot> values) {
    }

    record OptionValueSnapshot(Long valueId, String value) {
    }

    record ResolvedOption(Long groupId, String groupName, Long valueId, String value) {
    }
}
