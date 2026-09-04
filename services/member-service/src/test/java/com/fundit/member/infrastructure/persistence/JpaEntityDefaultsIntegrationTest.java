package com.fundit.member.infrastructure.persistence;

import com.fundit.member.infrastructure.persistence.address.AddressJpaEntity;
import com.fundit.member.infrastructure.persistence.address.AddressJpaRepository;
import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import com.fundit.member.infrastructure.persistence.termsagreement.TermsAgreementJpaEntity;
import com.fundit.member.infrastructure.persistence.termsagreement.TermsAgreementJpaRepository;
import com.fundit.member.infrastructure.persistence.wish.WishJpaEntity;
import com.fundit.member.infrastructure.persistence.wish.WishJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 각 JpaEntity의 @PrePersist 기본값 처리(createdAt/updatedAt/isDefault/agreedAt)는
 * 서비스 유닛테스트(빌더로 직접 생성, 리포지토리는 목킹)에서는 절대 실행되지 않는다 —
 * 실제 Hibernate 라이프사이클을 타야만 검증 가능해 통합테스트로 분리한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class JpaEntityDefaultsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private MemberJpaRepository memberJpaRepository;
    @Autowired
    private AddressJpaRepository addressJpaRepository;
    @Autowired
    private TermsAgreementJpaRepository termsAgreementJpaRepository;
    @Autowired
    private WishJpaRepository wishJpaRepository;

    private UUID createMember() {
        return memberJpaRepository.save(MemberJpaEntity.builder()
                .id(UUID.randomUUID()).name("홍길동").phoneNumber("01012345678").build()).getId();
    }

    @Test
    void 회원_저장시_생성시각을_지정하지_않으면_자동으로_채워진다() {
        // when
        MemberJpaEntity saved = memberJpaRepository.save(MemberJpaEntity.builder()
                .id(UUID.randomUUID()).name("홍길동").phoneNumber("01012345678").build());

        // then
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void 회원_저장시_생성시각을_지정하면_그대로_유지된다() {
        // given
        Instant fixed = Instant.parse("2020-01-01T00:00:00Z");

        // when
        MemberJpaEntity saved = memberJpaRepository.save(MemberJpaEntity.builder()
                .id(UUID.randomUUID()).name("홍길동").phoneNumber("01012345678")
                .createdAt(fixed).updatedAt(fixed).build());

        // then
        assertThat(saved.getCreatedAt()).isEqualTo(fixed);
        assertThat(saved.getUpdatedAt()).isEqualTo(fixed);
    }

    @Test
    void 배송지_저장시_기본값을_지정하지_않으면_생성시각과_isDefault가_채워진다() {
        // given
        UUID memberId = createMember();

        // when
        AddressJpaEntity saved = addressJpaRepository.save(AddressJpaEntity.builder()
                .memberId(memberId).recipientName("홍길동").phoneNumber("01012345678")
                .zipcode("12345").addressLine1("테헤란로 1").build());

        // then
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getIsDefault()).isFalse();
    }

    @Test
    void 배송지_저장시_기본_배송지_여부를_지정하면_그대로_유지된다() {
        // given
        UUID memberId = createMember();

        // when
        AddressJpaEntity saved = addressJpaRepository.save(AddressJpaEntity.builder()
                .memberId(memberId).recipientName("홍길동").phoneNumber("01012345678")
                .zipcode("12345").addressLine1("테헤란로 1")
                .isDefault(true).createdAt(Instant.parse("2020-01-01T00:00:00Z")).build());

        // then
        assertThat(saved.getIsDefault()).isTrue();
    }

    @Test
    void 약관동의_저장시_동의시각을_지정하지_않으면_자동으로_채워진다() {
        // given
        UUID memberId = createMember();

        // when
        TermsAgreementJpaEntity saved = termsAgreementJpaRepository.save(TermsAgreementJpaEntity.builder()
                .memberId(memberId).termsType("SERVICE_USE").termsVersion("1.0").agreed(true).build());

        // then
        assertThat(saved.getAgreedAt()).isNotNull();
    }

    @Test
    void 약관동의_저장시_동의시각을_지정하면_그대로_유지된다() {
        // given
        UUID memberId = createMember();
        Instant fixed = Instant.parse("2020-01-01T00:00:00Z");

        // when
        TermsAgreementJpaEntity saved = termsAgreementJpaRepository.save(TermsAgreementJpaEntity.builder()
                .memberId(memberId).termsType("SERVICE_USE").termsVersion("1.0").agreed(true).agreedAt(fixed).build());

        // then
        assertThat(saved.getAgreedAt()).isEqualTo(fixed);
    }

    @Test
    void 찜을_직접_저장하면_생성시각이_자동으로_채워진다() {
        // given
        UUID memberId = createMember();

        // when
        WishJpaEntity saved = wishJpaRepository.save(WishJpaEntity.builder()
                .memberId(memberId).projectId(100L).build());

        // then
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void 찜_저장시_생성시각을_지정하면_그대로_유지된다() {
        // given
        UUID memberId = createMember();
        Instant fixed = Instant.parse("2020-01-01T00:00:00Z");

        // when
        WishJpaEntity saved = wishJpaRepository.save(WishJpaEntity.builder()
                .memberId(memberId).projectId(101L).createdAt(fixed).build());

        // then
        assertThat(saved.getCreatedAt()).isEqualTo(fixed);
    }
}
