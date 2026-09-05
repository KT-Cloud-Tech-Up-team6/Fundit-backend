package com.fundit.project.infrastructure.persistence.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommunityAnswerJpaRepository extends JpaRepository<CommunityAnswerJpaEntity, Long> {

    Optional<CommunityAnswerJpaEntity> findByPostId(Long postId);

    List<CommunityAnswerJpaEntity> findByPostIdIn(List<Long> postIds);
}
