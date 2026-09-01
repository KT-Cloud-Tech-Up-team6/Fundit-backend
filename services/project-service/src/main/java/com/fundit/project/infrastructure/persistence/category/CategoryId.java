package com.fundit.project.infrastructure.persistence.category;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@Embeddable
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryId implements Serializable {

    @Column(name = "category_major", nullable = false, length = 50)
    private String categoryMajor;

    @Column(name = "category_minor", nullable = false, length = 50)
    private String categoryMinor;
}
