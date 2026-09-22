package com.fundit.project.domain.reward;

import java.util.List;

/**
 * 리워드 옵션 그룹(예: 색상) + 그 값 목록(예: 화이트, 블랙). has_option=true인 리워드에만 존재.
 *
 * <p>{@code id}는 PATCH 시 기존 그룹을 식별해 ID를 유지한 채 이름/값만 갱신하기 위한 값이다
 * (null이면 신규 그룹). {@link RewardRepository#replaceOptions}가 이 값으로 유지/추가/삭제를
 * 판단한다.
 */
public record RewardOptionGroup(Long id, String groupName, List<String> values) {

    /** 신규 그룹(등록, 또는 ID 지정 없이 추가하는 옵션) 생성용 편의 생성자. */
    public RewardOptionGroup(String groupName, List<String> values) {
        this(null, groupName, values);
    }
}
