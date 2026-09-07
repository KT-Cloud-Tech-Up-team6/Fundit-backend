package com.fundit.project.domain.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.ProjectErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — isLimited/quantity 정합성 같은
 * 불변식이 있어 도메인/영속성을 완전히 분리한다(chk_rewards_quantity DB 제약과 동일 규칙을
 * 애플리케이션에서도 먼저 검증해 400으로 응답한다).
 */
@Getter
@Builder(toBuilder = true)
public class Reward {

    private final Long id;
    private final Long projectId;
    private String name;
    private String description;
    private String imageUrl;
    private Long price;
    private boolean isLimited;
    private Integer quantity;
    private boolean isEarlyBird;
    private boolean hasOption;
    private int sortOrder;
    private String categoryType;
    private Map<String, String> disclosure;
    private boolean simpleRefundDisabled;
    private List<RewardOptionGroup> optionGroups;
    private final String rewardDisplayCode;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public static Reward create(Long projectId, String name, String description, String imageUrl, Long price,
                                 boolean isLimited, Integer quantity, boolean isEarlyBird,
                                 List<RewardOptionGroup> optionGroups) {
        validateQuantity(isLimited, quantity);
        return Reward.builder()
                .projectId(projectId)
                .name(name)
                .description(description)
                .imageUrl(imageUrl)
                .price(price)
                .isLimited(isLimited)
                .quantity(quantity)
                .isEarlyBird(isEarlyBird)
                .hasOption(optionGroups != null && !optionGroups.isEmpty())
                .optionGroups(optionGroups == null ? List.of() : optionGroups)
                .sortOrder(0)
                .simpleRefundDisabled(false)
                .build();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * PATCH .../rewards/{id} — 병합(전달되지 않은 필드는 기존값 유지)은 application 계층에서
     * 끝내고, 이 메서드는 최종적으로 반영될 값만 받아 불변식을 재검증한다.
     */
    public void changeBasicInfo(String name, String description, String imageUrl, Long price,
                                 boolean isLimited, Integer quantity, boolean isEarlyBird,
                                 List<RewardOptionGroup> optionGroups) {
        validateQuantity(isLimited, quantity);
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
        this.isLimited = isLimited;
        this.quantity = quantity;
        this.isEarlyBird = isEarlyBird;
        if (optionGroups != null) {
            this.optionGroups = optionGroups;
            this.hasOption = !optionGroups.isEmpty();
        }
    }

    public void changeDisclosure(String categoryType, Map<String, String> disclosure) {
        this.categoryType = categoryType;
        this.disclosure = disclosure;
    }

    public void changeRefundPolicy(boolean simpleRefundDisabled) {
        this.simpleRefundDisabled = simpleRefundDisabled;
    }

    public void delete() {
        this.deletedAt = Instant.now();
    }

    private static void validateQuantity(boolean isLimited, Integer quantity) {
        boolean valid = isLimited ? (quantity != null && quantity >= 0) : quantity == null;
        if (!valid) {
            throw new BusinessException(ProjectErrorCode.INVALID_REWARD_QUANTITY);
        }
    }
}
