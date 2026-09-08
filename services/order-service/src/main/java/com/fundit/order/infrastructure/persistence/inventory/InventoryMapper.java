package com.fundit.order.infrastructure.persistence.inventory;

import com.fundit.order.domain.inventory.Inventory;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    Inventory toDomain(InventoryJpaEntity entity) {
        return Inventory.builder()
                .id(entity.getId())
                .rewardId(entity.getRewardId())
                .availableStock(entity.getAvailableStock())
                .reservedStock(entity.getReservedStock())
                .initialQuantity(entity.getInitialQuantity())
                .version(entity.getVersion())
                .build();
    }

    InventoryJpaEntity toEntity(Inventory domain) {
        return InventoryJpaEntity.builder()
                .id(domain.getId())
                .rewardId(domain.getRewardId())
                .availableStock(domain.getAvailableStock())
                .reservedStock(domain.getReservedStock())
                .initialQuantity(domain.getInitialQuantity())
                .version(domain.getVersion())
                .build();
    }
}
