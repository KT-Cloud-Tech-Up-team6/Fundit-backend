package com.fundit.search.infrastructure.persistence.category;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** categories의 복합 PK (category_major, category_minor). 마스터 데이터라 대리키를 두지 않는다. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CategoryId implements Serializable {

    private String categoryMajor;
    private String categoryMinor;
}
