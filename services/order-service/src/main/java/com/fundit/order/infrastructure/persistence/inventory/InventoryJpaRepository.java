package com.fundit.order.infrastructure.persistence.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, Long> {

    Optional<InventoryJpaEntity> findByRewardId(Long rewardId);

    List<InventoryJpaEntity> findByRewardIdIn(List<Long> rewardIds);

    void deleteByRewardId(Long rewardId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update InventoryJpaEntity i set i.availableStock = i.availableStock - :quantity, i.version = i.version + 1 "
            + "where i.rewardId = :rewardId and i.version = :version and i.availableStock >= :quantity")
    int decreaseAvailableStock(@Param("rewardId") Long rewardId, @Param("quantity") int quantity,
                                @Param("version") int version);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update InventoryJpaEntity i set i.availableStock = i.availableStock + :quantity, i.version = i.version + 1 "
            + "where i.rewardId = :rewardId and i.version = :version")
    int increaseAvailableStock(@Param("rewardId") Long rewardId, @Param("quantity") int quantity,
                                @Param("version") int version);

    // GREATEST()는 Postgres 함수라 이식성 있는 JPQL로 표현할 수 없어 네이티브 쿼리로 작성한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "update inventories set available_stock = GREATEST(available_stock + :delta, 0), "
            + "initial_quantity = :newInitialQuantity, version = version + 1 "
            + "where reward_id = :rewardId and version = :version", nativeQuery = true)
    int applyQuantityDelta(@Param("rewardId") Long rewardId, @Param("delta") int delta,
                           @Param("newInitialQuantity") int newInitialQuantity, @Param("version") int version);
}
