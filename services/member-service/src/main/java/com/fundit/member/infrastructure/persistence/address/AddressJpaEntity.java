package com.fundit.member.infrastructure.persistence.address;

import com.fundit.member.infrastructure.persistence.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/** 단순 애그리거트(persistence-convention.md §2) — 회원당 다건, 불변식 없음. */
@Getter
@Entity
@Builder
@Table(name = "addresses")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AddressJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    /** 배송지 개인정보는 암호문으로 저장된다(security.md S9). {@link EncryptedStringConverter} 참고. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(nullable = false, length = 10)
    private String zipcode;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = Instant.now();
        if (this.isDefault == null) this.isDefault = false;
    }
}
