package com.fundit.order.infrastructure.persistence.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CouponJpaRepository extends JpaRepository<CouponJpaEntity, Long> {

    Optional<CouponJpaEntity> findByCouponCode(String couponCode);

    List<CouponJpaEntity> findByCouponCodeIn(Collection<String> couponCodes);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CouponJpaEntity c set c.remainingQuantity = c.remainingQuantity - 1, c.version = c.version + 1 "
            + "where c.couponCode = :couponCode and c.version = :version and c.remainingQuantity > 0")
    int decreaseRemainingQuantity(@Param("couponCode") String couponCode, @Param("version") int version);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CouponJpaEntity c set c.usedBudgetAmount = c.usedBudgetAmount + :amount, c.version = c.version + 1 "
            + "where c.couponCode = :couponCode and c.version = :version")
    int increaseUsedBudget(@Param("couponCode") String couponCode, @Param("amount") long amount, @Param("version") int version);
}
