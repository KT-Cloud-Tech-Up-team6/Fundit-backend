package com.fundit.project.infrastructure.persistence.aifundingstory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface FundingStorySessionJpaRepository extends JpaRepository<FundingStorySessionJpaEntity, UUID> {

    Optional<FundingStorySessionJpaEntity> findByProjectIdAndIdempotencyKey(Long projectId, String idempotencyKey);

    /**
     * uq_ai_funding_story_sessions_project_idempotency_key를 충돌 대상으로 삼는 멱등 INSERT(#226).
     * 같은 키를 동시에 쓰려는 경합을 DB가 판정하게 해 제약 위반으로 트랜잭션이 깨지는 것을 막는다 —
     * 0을 돌려주면 다른 요청이 키를 선점했다는 뜻이다. 결과·추가질문이 없는 새 행 전용.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO ai_funding_story_sessions
                (id, project_id, seller_id, product_description, product_image_urls, answers,
                 status, additional_questions, result, idempotency_key, created_at, updated_at)
            VALUES (:id, :projectId, :sellerId, :productDescription, '[]'::jsonb, '[]'::jsonb,
                    :status, NULL, NULL, :idempotencyKey, now(), now())
            ON CONFLICT (project_id, idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
            """, nativeQuery = true)
    int insertIfKeyFree(
            @Param("id") UUID id,
            @Param("projectId") Long projectId,
            @Param("sellerId") UUID sellerId,
            @Param("productDescription") String productDescription,
            @Param("status") String status,
            @Param("idempotencyKey") String idempotencyKey);
}
