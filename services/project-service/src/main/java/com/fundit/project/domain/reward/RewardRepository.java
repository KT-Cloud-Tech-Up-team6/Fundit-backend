package com.fundit.project.domain.reward;

import java.util.List;
import java.util.Optional;

public interface RewardRepository {

    /** 리워드 기본 컬럼만 저장한다(옵션 그룹/값 테이블은 건드리지 않음). */
    Reward save(Reward reward);

    /** 옵션 그룹/값을 통째로 치환한다(삭제 후 재삽입). 옵션을 등록/수정할 때만 호출한다. */
    void replaceOptions(Long rewardId, List<RewardOptionGroup> optionGroups);

    /** 소프트 삭제된 리워드는 제외한다. */
    Optional<Reward> findById(Long id);
}
