package com.fundit.project.infrastructure.persistence.community;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostJpaRepository extends JpaRepository<CommunityPostJpaEntity, Long> {

    @Query("""
            select p from CommunityPostJpaEntity p
            where p.projectId = :projectId
              and (:postType is null or p.postType = :postType)
              and (:answeredOnly = false or not exists (
                    select 1 from CommunityAnswerJpaEntity a where a.postId = p.id))
            """)
    Page<CommunityPostJpaEntity> findList(
            @Param("projectId") Long projectId, @Param("postType") String postType,
            @Param("answeredOnly") boolean answeredOnly, Pageable pageable);
}
