package com.fundit.order.infrastructure.persistence.funding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FundingLineItemJpaRepository extends JpaRepository<FundingLineItemJpaEntity, Long> {

    List<FundingLineItemJpaEntity> findByFundingId(Long fundingId);

    /**
     * PROJECT-015 — 리워드 단위 합계(optionValueId=null) + 옵션값 단위 구매수량/금액.
     * 결제완료(FUNDING_IN_PROGRESS/GOAL_ACHIEVED)만 집계한다.
     *
     * <p>UNION ALL인 이유: 한 라인에 옵션이 2개 이상(예: 색상+사이즈)이면 {@code funding_line_item_options}에
     * 라인당 여러 행이 생겨, 예전처럼 그 값으로만 GROUP BY하면 rewardId 하나에 옵션값 행이 여러 개
     * 생기고 각 행이 라인 전체 수량을 그대로 들고 있어 — 옵션값 행들을 전부 더해 "리워드 총 판매량"을
     * 구하면 옵션 차원 수만큼 부풀려진다. 그래서 리워드 단위 합계(첫 번째 SELECT, 옵션 조인 없음)를
     * 별도 행으로 항상 내려주고, 옵션값별 분해(두 번째 SELECT)는 그 아래 참고용으로만 쓴다 —
     * 소비 측은 옵션값 행들을 합산하지 않고 optionValueId=null 행의 값을 총 판매량으로 쓴다.
     *
     * <p>레거시 {@code project_id}(Long)가 아니라 {@code project_public_id}(UUID)로 조인한다 —
     * 이유는 {@link FundingJpaRepository#findDistinctProjectPublicIdsWithCountableFundings()} 참고.
     */
    @Query(value = """
            SELECT li.reward_id AS rewardId, CAST(NULL AS BIGINT) AS optionValueId,
                   SUM(li.quantity) AS totalQuantity, SUM(li.quantity * li.unit_price) AS totalAmount
            FROM funding_line_items li
            JOIN fundings f ON f.id = li.funding_id
            WHERE f.project_public_id = :projectId AND f.status IN ('FUNDING_IN_PROGRESS','GOAL_ACHIEVED')
            GROUP BY li.reward_id

            UNION ALL

            SELECT li.reward_id AS rewardId, o.option_value_id AS optionValueId,
                   SUM(li.quantity) AS totalQuantity, SUM(li.quantity * li.unit_price) AS totalAmount
            FROM funding_line_items li
            JOIN fundings f ON f.id = li.funding_id
            JOIN funding_line_item_options o ON o.funding_line_item_id = li.id
            WHERE f.project_public_id = :projectId AND f.status IN ('FUNDING_IN_PROGRESS','GOAL_ACHIEVED')
            GROUP BY li.reward_id, o.option_value_id
            """, nativeQuery = true)
    List<RewardStatProjection> aggregateRewardStatsByProjectId(@Param("projectId") UUID projectId);

    interface RewardStatProjection {
        Long getRewardId();
        Long getOptionValueId();
        Integer getTotalQuantity();
        Long getTotalAmount();
    }
}
