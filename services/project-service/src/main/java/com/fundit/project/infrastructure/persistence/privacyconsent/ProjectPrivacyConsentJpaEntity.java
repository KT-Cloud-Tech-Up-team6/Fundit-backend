package com.fundit.project.infrastructure.persistence.privacyconsent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 단순 애그리거트(persistence-convention.md §2) — append-only 동의 이력(법적 근거자료,
 * V2__add_project_story_and_privacy_consent.sql). UPDATE/DELETE API를 두지 않는다.
 */
@Getter
@Entity
@Builder
@Table(name = "project_privacy_consents")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectPrivacyConsentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Boolean agreed;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @PrePersist
    protected void onCreate() {
        if (this.consentedAt == null) this.consentedAt = Instant.now();
    }
}
