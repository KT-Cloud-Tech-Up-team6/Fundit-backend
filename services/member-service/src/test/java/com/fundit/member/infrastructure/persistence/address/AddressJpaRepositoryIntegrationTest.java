package com.fundit.member.infrastructure.persistence.address;

import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 부분 유니크 인덱스(V5)와 벌크 해제 쿼리는 실제 DB에서만 확인된다. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "member.encryption.key=Oz9qy5geAzhDXyHfZdFB3WPwVH8/sx/uD2j5rX5DGkY=",
        "internal-api.key=test-only-internal-api-key"})
@Transactional
class AddressJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private AddressJpaRepository addressJpaRepository;
    @Autowired
    private MemberJpaRepository memberJpaRepository;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private UUID createMember() {
        return memberJpaRepository.save(MemberJpaEntity.builder()
                .id(UUID.randomUUID()).name("홍길동").phoneNumber("01012345678").build()).getId();
    }

    private AddressJpaEntity address(UUID memberId, boolean isDefault) {
        return AddressJpaEntity.builder().memberId(memberId).recipientName("홍길동").phoneNumber("01012345678")
                .zipcode("12345").addressLine1("테헤란로 1").isDefault(isDefault).build();
    }

    @Test
    void 한_회원에_기본_배송지를_두개_저장하면_DB가_거부한다() {
        // given
        UUID memberId = createMember();
        addressJpaRepository.saveAndFlush(address(memberId, true));

        // when & then
        assertThatThrownBy(() -> addressJpaRepository.saveAndFlush(address(memberId, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 기존_기본을_해제하면_새_기본을_저장할_수_있다() {
        // given
        UUID memberId = createMember();
        AddressJpaEntity first = addressJpaRepository.saveAndFlush(address(memberId, true));

        // when
        addressJpaRepository.clearDefault(memberId);
        addressJpaRepository.saveAndFlush(address(memberId, true));
        // 벌크 UPDATE는 영속성 컨텍스트를 갱신하지 않는다 — DB 값을 다시 읽는다
        entityManager.clear();

        // then
        assertThat(addressJpaRepository.findByMemberId(memberId))
                .filteredOn(AddressJpaEntity::getIsDefault).hasSize(1)
                .extracting(AddressJpaEntity::getId).doesNotContain(first.getId());
    }
}
