package com.fundit.order.infrastructure.persistence.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고 원장(inventories) JPA 매핑 전용. updated_at은 DB 트리거(set_updated_at)가 관리하므로
 * 여기서는 매핑하지 않는다. 실제 증감은 {@link InventoryJpaRepository}의 조건부 @Modifying
 * 쿼리로만 하고, 이 엔티티를 로드해 필드를 바꾼 뒤 save()하는 흐름(TOCTOU)은 쓰지 않는다.
 */
@Getter
@Entity
@Builder
@Table(name = "inventories")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reward_id", nullable = false)
    private Long rewardId;

    @Column(name = "available_stock", nullable = false)
    private int availableStock;

    @Column(name = "reserved_stock", nullable = false)
    private int reservedStock;

    @Column(name = "initial_quantity", nullable = false)
    private int initialQuantity;

    @Column(nullable = false)
    private int version;
}
