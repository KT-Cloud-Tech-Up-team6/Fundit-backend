package com.fundit.project.domain.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * 리워드 애그리거트. 가격 불변식과 소프트 삭제를 소유한다.
 * isUnlimited는 옵션 재고 정책과 얽혀 있어 생성 이후 변경할 수 없다(변경이 필요하면 리워드를 새로 등록).
 */
@Getter
@Builder
public class Reward {

    private final Long id;
    private final Long projectId;

    private String name;
    private String description;
    private Long price;
    private final boolean unlimited;
    private boolean earlyBird;
    private boolean simpleRefundDisabled;
    private String categoryType;
    private Map<String, Object> disclosure;
    private int displayOrder;

    private final Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public static Reward create(Long projectId,
                                String name,
                                String description,
                                Long price,
                                boolean unlimited,
                                boolean earlyBird,
                                boolean simpleRefundDisabled,
                                String categoryType,
                                Map<String, Object> disclosure) {
        requireValidPrice(price);
        return Reward.builder()
                .projectId(projectId)
                .name(name)
                .description(description)
                .price(price)
                .unlimited(unlimited)
                .earlyBird(earlyBird)
                .simpleRefundDisabled(simpleRefundDisabled)
                .categoryType(categoryType)
                .disclosure(disclosure)
                .build();
    }

    public void update(String name,
                       String description,
                       Long price,
                       Boolean earlyBird,
                       Boolean simpleRefundDisabled,
                       String categoryType,
                       Map<String, Object> disclosure,
                       Integer displayOrder) {
        if (price != null) {
            requireValidPrice(price);
            this.price = price;
        }
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (earlyBird != null) {
            this.earlyBird = earlyBird;
        }
        if (simpleRefundDisabled != null) {
            this.simpleRefundDisabled = simpleRefundDisabled;
        }
        if (categoryType != null) {
            this.categoryType = categoryType;
        }
        if (disclosure != null) {
            this.disclosure = disclosure;
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    private static void requireValidPrice(Long price) {
        if (price == null || price < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "리워드 가격은 0원 이상이어야 합니다.");
        }
    }
}
