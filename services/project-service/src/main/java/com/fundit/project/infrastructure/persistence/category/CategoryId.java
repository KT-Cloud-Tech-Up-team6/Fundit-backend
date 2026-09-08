package com.fundit.project.infrastructure.persistence.category;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** categories 복합 PK(category_major, category_minor). */
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class CategoryId implements Serializable {
    private String categoryMajor;
    private String categoryMinor;
}
