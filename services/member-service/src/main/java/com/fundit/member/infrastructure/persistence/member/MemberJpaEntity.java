package com.fundit.member.infrastructure.persistence.member;

import com.fundit.member.infrastructure.persistence.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 단순 애그리거트(persistence-convention.md §2) — 불변식·상태전이가 없어
 * domain/Mapper/Adapter 없이 application이 이 JpaEntity를 직접 사용한다.
 */
@Getter
@Entity
@Builder
@Table(name = "members")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberJpaEntity {

    @Id
    private UUID id;

    /**
     * 암호문으로 저장된다(security.md S9). 애플리케이션 코드는 평문만 본다 —
     * 변환은 {@link EncryptedStringConverter}가 한다.
     *
     * <p><b>이 컬럼으로는 검색할 수 없다.</b> 같은 평문도 매번 다른 암호문이 된다.
     * {@code length}를 지정하지 않는 이유: 암호문 길이가 평문과 달라 의미가 없고, DDL도 TEXT다.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false)
    private String name;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(length = 50)
    private String nickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
