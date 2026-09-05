package com.fundit.project.infrastructure.persistence.aifundingstory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiFundingStorySessionJpaRepository extends JpaRepository<AiFundingStorySessionJpaEntity, UUID> {
}
