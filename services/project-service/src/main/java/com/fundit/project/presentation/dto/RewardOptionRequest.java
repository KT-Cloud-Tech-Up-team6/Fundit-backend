package com.fundit.project.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * {@code optionGroupId}는 재편집 화면이 GET .../rewards/mine 응답의 {@code groupId}를 그대로
 * 되돌려 보내는 값이다 — 있으면 해당 그룹을 유지한 채 이름/값만 갱신하고, 없으면 신규 그룹으로
 * 취급한다(RewardPersistenceAdapter.replaceOptions 참고). 값 단위 ID는 없다 — 그룹의 값 목록은
 * 항상 통째로 교체된다(개별 값 ID를 참조하는 곳이 없어 그룹 단위 안정성이면 충분하다).
 */
public record RewardOptionRequest(
        Long optionGroupId,
        @NotBlank String groupName,
        @NotEmpty List<@NotBlank String> values
) {
}
