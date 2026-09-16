package com.fundit.member.infrastructure.persistence.follow;

import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ON CONFLICT DO NOTHING과 members 조인은 Postgres에서 실행해봐야 확인된다(찜과 같은 이유).
 *
 * <p>내부 키·@Transactional이 필요한 사정은 {@code WishJpaRepositoryIntegrationTest} 주석 참고.
 * 워커를 끄는 이유: 브로커가 없어 UnconfiguredWishEventTransport가 매 주기 예외를 던져 로그가 섞인다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        "wish-event-outbox.worker-enabled=false"})
@Transactional
class FollowJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private FollowJpaRepository followJpaRepository;
    @Autowired
    private MemberJpaRepository memberJpaRepository;

    private UUID createMember(String name) {
        return memberJpaRepository.save(MemberJpaEntity.builder()
                .id(UUID.randomUUID()).name(name).phoneNumber("01012345678").build()).getId();
    }

    @Test
    void 같은_판매자를_두번_팔로우해도_한_행만_남는다() {
        // given
        UUID memberId = createMember("구매자");
        UUID sellerId = createMember("판매자");

        // when
        followJpaRepository.insertIgnoringConflict(memberId, sellerId);
        followJpaRepository.insertIgnoringConflict(memberId, sellerId);

        // then
        assertThat(followJpaRepository.findViewsByMemberId(memberId, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(1);
    }

    @Test
    void 팔로우하지_않은_판매자를_언팔로우해도_예외가_발생하지_않는다() {
        // given
        UUID memberId = createMember("구매자");

        // when & then (예외 없이 완료되면 통과)
        followJpaRepository.deleteByMemberIdAndSellerId(memberId, UUID.randomUUID());
    }

    @Test
    void 목록은_판매자_이름을_조인해서_돌려준다() {
        // given
        UUID memberId = createMember("구매자");
        UUID sellerId = createMember("김판매");
        followJpaRepository.insertIgnoringConflict(memberId, sellerId);

        // when
        FollowView view = followJpaRepository.findViewsByMemberId(memberId, PageRequest.of(0, 20))
                .getContent().get(0);

        // then
        assertThat(view.sellerId()).isEqualTo(sellerId);
        assertThat(view.sellerName()).isEqualTo("김판매");
        assertThat(view.createdAt()).isNotNull();
    }

    /** 탈퇴한 판매자가 목록에 남으면 클릭했을 때 없는 프로필로 간다. */
    @Test
    void 탈퇴한_판매자는_목록에서_제외된다() {
        // given
        UUID memberId = createMember("구매자");
        UUID sellerId = memberJpaRepository.save(MemberJpaEntity.builder()
                .id(UUID.randomUUID()).name("탈퇴판매자").phoneNumber("01012345678")
                .deletedAt(Instant.now()).build()).getId();
        followJpaRepository.insertIgnoringConflict(memberId, sellerId);

        // when & then
        assertThat(followJpaRepository.findViewsByMemberId(memberId, PageRequest.of(0, 20)).getTotalElements())
                .isEqualTo(0);
    }
}
