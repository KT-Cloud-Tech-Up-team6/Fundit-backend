package com.fundit.search.infrastructure.persistence.recentkeyword;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** recent_search_keywords의 복합 PK (member_id, keyword). */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RecentSearchKeywordId implements Serializable {

    private UUID memberId;
    private String keyword;
}
