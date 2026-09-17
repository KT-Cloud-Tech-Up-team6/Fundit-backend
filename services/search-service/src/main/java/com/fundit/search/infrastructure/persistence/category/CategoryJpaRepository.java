package com.fundit.search.infrastructure.persistence.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, CategoryId> {

    List<CategoryJpaEntity> findAllByOrderByCategoryMajorAscDisplayOrderAsc();

    boolean existsByCategoryMajor(String categoryMajor);

    boolean existsByCategoryMajorAndCategoryMinor(String categoryMajor, String categoryMinor);
}
