package com.fundit.member.infrastructure.persistence.address;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressJpaRepository extends JpaRepository<AddressJpaEntity, Long> {

    List<AddressJpaEntity> findByMemberId(UUID memberId);

    /** 본인 소유 확인을 조회에 묶는다 — 남의 배송지 id면 비어 있다(S4). */
    Optional<AddressJpaEntity> findByIdAndMemberId(Long id, UUID memberId);

    /**
     * 회원의 기본 배송지를 해제한다. {@code keepId}는 제외한다 — 이미 영속성 컨텍스트에 로딩된
     * 대상 행까지 이 벌크 UPDATE로 false가 되면, 이후 {@code markDefault()}가 스냅샷과 같다고
     * 판단돼 UPDATE가 나가지 않아 기본값이 사라진다.
     *
     * <p>DB의 부분 유니크 인덱스(uq_addresses_member_default)가 기본 1개를 보장하므로,
     * 새 기본을 쓰기 <b>전에</b> 호출해야 한다.
     */
    @Transactional
    @Modifying
    @Query("update AddressJpaEntity a set a.isDefault = false "
            + "where a.memberId = :memberId and a.isDefault = true and a.id <> :keepId")
    void clearDefaultExcept(@Param("memberId") UUID memberId, @Param("keepId") Long keepId);

    /** 새 배송지 등록용 — 아직 id가 없어 제외할 행이 없다(IDENTITY는 1부터 시작). */
    default void clearDefault(UUID memberId) {
        clearDefaultExcept(memberId, 0L);
    }
}
