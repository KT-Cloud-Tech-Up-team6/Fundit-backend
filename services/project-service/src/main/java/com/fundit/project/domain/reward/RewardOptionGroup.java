package com.fundit.project.domain.reward;

import java.util.List;

/** 리워드 옵션 그룹(예: 색상) + 그 값 목록(예: 화이트, 블랙). has_option=true인 리워드에만 존재. */
public record RewardOptionGroup(String groupName, List<String> values) {
}
