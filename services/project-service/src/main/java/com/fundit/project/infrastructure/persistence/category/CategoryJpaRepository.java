package com.fundit.project.infrastructure.persistence.category;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, CategoryId> {

    boolean existsByIdCategoryMajorAndIdCategoryMinor(String categoryMajor, String categoryMinor);
}
