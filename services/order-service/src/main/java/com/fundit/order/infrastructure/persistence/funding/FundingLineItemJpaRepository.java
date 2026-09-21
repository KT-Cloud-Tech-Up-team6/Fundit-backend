package com.fundit.order.infrastructure.persistence.funding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FundingLineItemJpaRepository extends JpaRepository<FundingLineItemJpaEntity, Long> {

    List<FundingLineItemJpaEntity> findByFundingId(Long fundingId);

    /**
     * PROJECT-015 — 리워드+옵션값 단위 구매수량/금액. 결제완료(FUNDING_IN_PROGRESS/GOAL_ACHIEVED)만.
     * 라인에 옵션이 여러 개면 옵션값마다 같은 수량/금액을 잡는다(옵션값 한정 통계).
     */
    @Query(value = """
            SELECT li.reward_id AS rewardId, o.option_value_id AS optionValueId,
                   SUM(li.quantity) AS totalQuantity, SUM(li.quantity * li.unit_price) AS totalAmount
            FROM funding_line_items li
            JOIN fundings f ON f.id = li.funding_id
            LEFT JOIN funding_line_item_options o ON o.funding_line_item_id = li.id
            WHERE f.project_id = :projectId AND f.status IN ('FUNDING_IN_PROGRESS','GOAL_ACHIEVED')
            GROUP BY li.reward_id, o.option_value_id
            """, nativeQuery = true)
    List<RewardStatProjection> aggregateRewardStatsByProjectId(@Param("projectId") Long projectId);

    interface RewardStatProjection {
        Long getRewardId();
        Long getOptionValueId();
        Integer getTotalQuantity();
        Long getTotalAmount();
    }
}
