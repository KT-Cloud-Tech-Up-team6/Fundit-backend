package com.fundit.member.infrastructure.persistence.address;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AddressJpaRepository extends JpaRepository<AddressJpaEntity, Long> {

    List<AddressJpaEntity> findByMemberId(UUID memberId);
}
