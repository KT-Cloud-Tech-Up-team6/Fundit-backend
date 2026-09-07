package com.fundit.project.infrastructure.persistence.wishstats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProjectWishStatJpaRepository extends JpaRepository<ProjectWishStatJpaEntity, Long> {

    /**
     * 같은 회원의 중복 ProjectWished는 카운트에 반영하지 않는다.
     * @Modifying 커스텀 쿼리는 호출부가 트랜잭션 안에 있어야 동작한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO project_wish_stat_members (project_id, member_id) "
            + "VALUES (:projectId, :memberId) ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertMemberIfAbsent(@Param("projectId") Long projectId, @Param("memberId") UUID memberId);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM project_wish_stat_members "
            + "WHERE project_id = :projectId AND member_id = :memberId", nativeQuery = true)
    int deleteMember(@Param("projectId") Long projectId, @Param("memberId") UUID memberId);

    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO project_wish_stats (project_id, wish_count) VALUES (:projectId, 1) "
            + "ON CONFLICT (project_id) DO UPDATE SET wish_count = project_wish_stats.wish_count + 1",
            nativeQuery = true)
    void incrementOrCreate(@Param("projectId") Long projectId);

    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE project_wish_stats SET wish_count = GREATEST(wish_count - 1, 0) "
            + "WHERE project_id = :projectId", nativeQuery = true)
    void decrementIfPresent(@Param("projectId") Long projectId);
}
