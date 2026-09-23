package com.fundit.member.infrastructure.persistence.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, UUID> {

    Optional<MemberJpaEntity> findByIdAndDeletedAtIsNull(UUID id);

    /** 판매자 닉네임 일괄 조회용. {@code ids}는 비어 있지 않게 넘긴다(JPQL {@code in}은 빈 컬렉션이 방언마다 다르다). */
    List<MemberJpaEntity> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
