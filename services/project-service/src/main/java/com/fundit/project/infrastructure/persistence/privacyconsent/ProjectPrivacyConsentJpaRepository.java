package com.fundit.project.infrastructure.persistence.privacyconsent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectPrivacyConsentJpaRepository extends JpaRepository<ProjectPrivacyConsentJpaEntity, Long> {

    boolean existsByProjectIdAndAgreedTrue(Long projectId);
}
