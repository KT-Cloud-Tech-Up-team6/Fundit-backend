package com.fundit.member.infrastructure.persistence.termsagreement;

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
import java.util.UUID;

/** 단순 애그리거트(persistence-convention.md §2) — append-only 동의 이력, UPDATE/DELETE 없음. */
@Getter
@Entity
@Builder
@Table(name = "terms_agreements")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreementJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "terms_type", nullable = false, length = 30)
    private String termsType;

    @Column(name = "terms_version", nullable = false, length = 20)
    private String termsVersion;

    @Column(nullable = false)
    private Boolean agreed;

    @Column(name = "agreed_at", nullable = false)
    private Instant agreedAt;

    @PrePersist
    protected void onCreate() {
        if (this.agreedAt == null) this.agreedAt = Instant.now();
    }
}
