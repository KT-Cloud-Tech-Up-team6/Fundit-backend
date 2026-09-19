package com.fundit.member.infrastructure.persistence;

import com.fundit.member.infrastructure.persistence.address.AddressJpaEntity;
import com.fundit.member.infrastructure.persistence.address.AddressJpaRepository;
import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>DB에 평문이 남지 않는지</b>를 실제 Postgres로 확인한다(security.md S9).
 *
 * <p>JPA로 읽으면 {@link EncryptedStringConverter}가 복호화해서 돌려주므로, 암호화를 하든
 * 안 하든 테스트가 통과한다. 그래서 여기서는 <b>네이티브 쿼리로 raw 컬럼 값을 직접 읽는다.</b>
 *
 * <p>Converter가 Spring 빈으로 주입되는지도 여기서 같이 드러난다 — 키를 못 받으면
 * 저장 자체가 실패한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "member.encryption.key=Oz9qy5geAzhDXyHfZdFB3WPwVH8/sx/uD2j5rX5DGkY=",
        "internal-api.key=test-only-internal-api-key",
        "member-event-outbox.worker-enabled=false"})
@Transactional
class MemberEncryptionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private MemberJpaRepository memberJpaRepository;
    @Autowired private AddressJpaRepository addressJpaRepository;
    @Autowired private EntityManager entityManager;

    private UUID saveMember() {
        UUID id = UUID.randomUUID();
        memberJpaRepository.save(MemberJpaEntity.builder()
                .id(id)
                .name("김펀딧")
                .phoneNumber("010-1234-5678")
                .nickname("펀딧")
                .build());
        return id;
    }

    private String rawColumn(String table, String column, Object id) {
        entityManager.flush();
        entityManager.clear();
        return (String) entityManager
                .createNativeQuery("select " + column + " from " + table + " where id = :id")
                .setParameter("id", id)
                .getSingleResult();
    }

    @Test
    void 이름과_전화번호는_평문으로_저장되지_않는다() {
        // given
        UUID id = saveMember();

        // when — JPA로 읽으면 복호화돼서 통과해버린다. raw 컬럼을 직접 본다
        String name = rawColumn("members", "name", id);
        String phone = rawColumn("members", "phone_number", id);

        // then
        assertThat(name).isNotEqualTo("김펀딧").doesNotContain("김펀딧");
        assertThat(phone).isNotEqualTo("010-1234-5678").doesNotContain("1234");
    }

    @Test
    void 저장했다_읽으면_평문_그대로다() {
        // given — 애플리케이션 코드는 암호화를 몰라야 한다
        UUID id = saveMember();
        entityManager.flush();
        entityManager.clear();

        // when
        MemberJpaEntity found = memberJpaRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();

        // then
        assertThat(found.getName()).isEqualTo("김펀딧");
        assertThat(found.getPhoneNumber()).isEqualTo("010-1234-5678");
        // 닉네임은 암호화 대상이 아니다 — 공개 표시용이라 검색·정렬이 필요해질 수 있다
        assertThat(rawColumn("members", "nickname", id)).isEqualTo("펀딧");
    }

    @Test
    void 같은_평문을_두_번_저장하면_암호문이_서로_다르다() {
        // given — IV를 재사용하면 같은 값이 같은 암호문이 되어 빈도 분석이 가능해진다
        UUID first = saveMember();
        UUID second = saveMember();

        // when & then
        assertThat(rawColumn("members", "phone_number", first))
                .isNotEqualTo(rawColumn("members", "phone_number", second));
    }

    @Test
    void 배송지_개인정보도_평문으로_저장되지_않는다() {
        // given
        UUID memberId = saveMember();
        Long addressId = addressJpaRepository.save(AddressJpaEntity.builder()
                .memberId(memberId)
                .recipientName("김수령")
                .phoneNumber("010-9999-8888")
                .zipcode("06234")
                .addressLine1("서울시 강남구 테헤란로 1")
                .addressLine2("101동 1001호")
                .isDefault(true)
                .build()).getId();

        // when & then
        assertThat(rawColumn("addresses", "recipient_name", addressId)).doesNotContain("김수령");
        assertThat(rawColumn("addresses", "address_line1", addressId)).doesNotContain("테헤란로");
        // 우편번호는 단독으로 개인을 식별할 수 없어 암호화 대상이 아니다(배송 권역 집계용)
        assertThat(rawColumn("addresses", "zipcode", addressId)).isEqualTo("06234");
    }
}
