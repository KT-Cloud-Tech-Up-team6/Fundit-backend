package com.fundit.project.domain.reward;

import java.util.List;
import java.util.Optional;

public interface RewardRepository {

    /** 리워드 기본 컬럼만 저장한다(옵션 그룹/값 테이블은 건드리지 않음). */
    Reward save(Reward reward);

    /**
     * 옵션 그룹/값을 통째로 치환한다(삭제 후 재삽입). 옵션을 등록/수정할 때만 호출한다.
     * 반환값은 실제로 영속화된 그룹 목록(신규/타 리워드 소속 그룹도 새로 부여된 ID 포함)이다 —
     * 응답 DTO가 요청값이 아닌 저장된 ID를 그대로 쓸 수 있도록 호출자가 사용한다.
     */
    List<RewardOptionGroup> replaceOptions(Long rewardId, List<RewardOptionGroup> optionGroups);

    /** 소프트 삭제된 리워드는 제외한다. */
    Optional<Reward> findById(Long id);

    /** 생성 요청 Idempotency-Key로 프로젝트 범위 중복 조회. 소프트 삭제된 리워드는 제외한다. */
    Optional<Reward> findByProjectIdAndIdempotencyKey(Long projectId, String idempotencyKey);

    /**
     * 소프트 삭제된 리워드는 제외한다.
     * 같은 리워드의 동시 PATCH 필드 병합을 직렬화하기 위해 비관적 락을 건다.
     */
    Optional<Reward> findByIdForUpdate(Long id);

    /** 소프트 삭제된 리워드는 제외한다. 정렬 순서(sortOrder) 오름차순. */
    List<Reward> findByProjectId(Long projectId);
}
