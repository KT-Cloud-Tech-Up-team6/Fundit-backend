package com.fundit.project.domain.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.ProjectErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

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
    private EarlyBirdDiscountType earlyBirdDiscountType;
    private Long earlyBirdDiscountValue;
    private boolean hasOption;
    private int sortOrder;
    private boolean simpleRefundDisabled;
    private List<RewardOptionGroup> optionGroups;
    private Long shippingFee;
    private Integer estimatedDeliveryDays;
    private final String rewardDisplayCode;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public static Reward create(Long projectId, String name, String description, String imageUrl, Long price,
                                 boolean isLimited, Integer quantity, boolean isEarlyBird,
                                 EarlyBirdDiscountType earlyBirdDiscountType, Long earlyBirdDiscountValue,
                                 List<RewardOptionGroup> optionGroups, Long shippingFee, Integer estimatedDeliveryDays) {
        validateQuantity(isLimited, quantity);
        validateEarlyBirdDiscount(isEarlyBird, earlyBirdDiscountType, earlyBirdDiscountValue, price);
        validateShippingInfo(shippingFee, estimatedDeliveryDays);
        return Reward.builder()
                .projectId(projectId)
                .name(name)
                .description(description)
                .imageUrl(imageUrl)
                .price(price)
                .isLimited(isLimited)
                .quantity(quantity)
                .isEarlyBird(isEarlyBird)
                .earlyBirdDiscountType(isEarlyBird ? earlyBirdDiscountType : null)
                .earlyBirdDiscountValue(isEarlyBird ? earlyBirdDiscountValue : null)
                .hasOption(optionGroups != null && !optionGroups.isEmpty())
                .optionGroups(optionGroups == null ? List.of() : optionGroups)
                .sortOrder(0)
                .simpleRefundDisabled(false)
                .shippingFee(shippingFee)
                .estimatedDeliveryDays(estimatedDeliveryDays)
                .build();
    }

    /** 얼리버드 할인 적용가. 얼리버드가 아니면 null. */
    public Long getEarlyBirdDiscountedPrice() {
        if (!isEarlyBird || earlyBirdDiscountType == null || earlyBirdDiscountValue == null) {
            return null;
        }
        return switch (earlyBirdDiscountType) {
            case AMOUNT -> price - earlyBirdDiscountValue;
            case RATE -> price - (price * earlyBirdDiscountValue / 100);
        };
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
                                 EarlyBirdDiscountType earlyBirdDiscountType, Long earlyBirdDiscountValue,
                                 List<RewardOptionGroup> optionGroups, Long shippingFee, Integer estimatedDeliveryDays) {
        validateQuantity(isLimited, quantity);
        validateEarlyBirdDiscount(isEarlyBird, earlyBirdDiscountType, earlyBirdDiscountValue, price);
        validateShippingInfo(shippingFee, estimatedDeliveryDays);
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.price = price;
        this.isLimited = isLimited;
        this.quantity = quantity;
        this.isEarlyBird = isEarlyBird;
        this.earlyBirdDiscountType = isEarlyBird ? earlyBirdDiscountType : null;
        this.earlyBirdDiscountValue = isEarlyBird ? earlyBirdDiscountValue : null;
        if (optionGroups != null) {
            this.optionGroups = optionGroups;
            this.hasOption = !optionGroups.isEmpty();
        }
        this.shippingFee = shippingFee;
        this.estimatedDeliveryDays = estimatedDeliveryDays;
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

    /**
     * 배송비/예상 발송일은 둘 다 선택값이라 null을 허용하고, 값이 있으면 0 이상이어야 한다
     * (chk_rewards_shipping_fee/chk_rewards_estimated_delivery_days DB 제약과 동일 규칙).
     * estimatedDeliveryDays는 "펀딩 종료 후 N일" 상대값이다.
     */
    private static void validateShippingInfo(Long shippingFee, Integer estimatedDeliveryDays) {
        boolean valid = (shippingFee == null || shippingFee >= 0)
                && (estimatedDeliveryDays == null || estimatedDeliveryDays >= 0);
        if (!valid) {
            throw new BusinessException(ProjectErrorCode.INVALID_REWARD_SHIPPING_INFO);
        }
    }

    /**
     * isEarlyBird=false면 할인 방식/값은 반드시 없어야 하고, true면 방식에 맞는 범위의 값이 있어야
     * 한다 — chk_rewards_early_bird_discount DB 제약과 동일 규칙(validateQuantity와 같은 패턴).
     */
    private static void validateEarlyBirdDiscount(boolean isEarlyBird, EarlyBirdDiscountType type, Long value, Long price) {
        if (!isEarlyBird) {
            if (type != null || value != null) {
                throw new BusinessException(ProjectErrorCode.INVALID_EARLY_BIRD_DISCOUNT);
            }
            return;
        }
        if (type == null || value == null) {
            throw new BusinessException(ProjectErrorCode.INVALID_EARLY_BIRD_DISCOUNT);
        }
        boolean valid = switch (type) {
            case AMOUNT -> value > 0 && value < price;
            case RATE -> value >= 0 && value <= 100;
        };
        if (!valid) {
            throw new BusinessException(ProjectErrorCode.INVALID_EARLY_BIRD_DISCOUNT);
        }
    }
}
