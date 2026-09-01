package com.fundit.project.infrastructure.persistence.engagement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProjectFollowJpaRepository extends JpaRepository<ProjectFollowJpaEntity, Long> {

    void deleteByProjectIdAndMemberId(Long projectId, UUID memberId);

    List<ProjectFollowJpaEntity> findByProjectId(Long projectId);

    /**
     * 같은 회원의 팔로우 요청이 동시에 들어와도 실패하지 않도록 중복은 DB에서 무시한다.
     * 조회로 먼저 걸러내는 방식은 조회와 INSERT 사이에 경합이 생겨 유니크 위반이 새어나간다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO project_follows (project_id, member_id)
            VALUES (:projectId, :memberId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("projectId") Long projectId, @Param("memberId") UUID memberId);
}
