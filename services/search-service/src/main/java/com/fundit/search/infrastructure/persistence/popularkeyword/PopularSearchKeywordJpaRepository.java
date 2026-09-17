package com.fundit.search.infrastructure.persistence.popularkeyword;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PopularSearchKeywordJpaRepository extends JpaRepository<PopularSearchKeywordJpaEntity, Integer> {

    List<PopularSearchKeywordJpaEntity> findAllByOrderByRankAsc();
}
