package com.fundit.project.infrastructure.persistence.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityAnswerJpaRepository extends JpaRepository<CommunityAnswerJpaEntity, Long> {

    Optional<CommunityAnswerJpaEntity> findByPostId(Long postId);

    List<CommunityAnswerJpaEntity> findByPostIdIn(List<Long> postIds);

    /**
     * 게시글당 답변 1개(uq_community_answers_post). 동시 요청이 둘 다 INSERT를 타도
     * 유니크 제약 위반이 호출부로 새지 않도록 ON CONFLICT로 갱신한다.
     * @Modifying 커스텀 쿼리는 호출부가 트랜잭션 안에 있어야 동작한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO community_answers (post_id, seller_id, content, created_at, updated_at)
            VALUES (:postId, :sellerId, :content, now(), now())
            ON CONFLICT (post_id) DO UPDATE
            SET content = EXCLUDED.content,
                seller_id = EXCLUDED.seller_id,
                updated_at = now()
            """, nativeQuery = true)
    void upsert(@Param("postId") Long postId, @Param("sellerId") UUID sellerId, @Param("content") String content);
}
