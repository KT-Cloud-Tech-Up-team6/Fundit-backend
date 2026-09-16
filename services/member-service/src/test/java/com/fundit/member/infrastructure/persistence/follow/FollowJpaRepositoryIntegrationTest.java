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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ON CONFLICT DO NOTHING과 members 조인은 Postgres에서 실행해봐야 확인된다(찜과 같은 이유).
 *
 * <p>내부 키·@Transactional이 필요한 사정은 {@code WishJpaRepositoryIntegrationTest} 주석 참고.
 * 워커를 끄는 이유: 이 테스트에 브로커가 없어 발행이 매 주기 실패하고 로그가 섞인다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "internal-api.key=test-only-internal-api-key",
        "member-event-outbox.worker-enabled=false"})
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

    /**
     * createdAt 동률은 실제로 생긴다 — insertIgnoringConflict가 Postgres now()(=트랜잭션 시작 시각)를
     * 쓴다. 2차 정렬 키가 없으면 DB가 힙 순서(=삽입 순서)로 돌려주므로, <b>오름차순으로 넣고
     * 내림차순을 기대</b>해 타이브레이커가 없으면 반드시 깨지게 만든다.
     */
    @Test
    void 등록시각이_같으면_판매자id_내림차순으로_정렬된다() {
        // given
        UUID memberId = createMember("구매자");
        Instant sameTime = Instant.parse("2026-09-16T00:00:00Z");
        List<UUID> ascending = Stream.generate(() -> createMember("판매자")).limit(5)
                // Postgres의 uuid 정렬은 바이트 단위 무부호 비교다 — Java UUID.compareTo(부호 있는 long 비교)와
                // 달라서, 16진 문자열 순서로 정렬해야 DB가 돌려주는 순서와 맞는다.
                .sorted(Comparator.comparing(UUID::toString)).toList();
        ascending.forEach(sellerId -> followJpaRepository.save(FollowJpaEntity.builder()
                .memberId(memberId).sellerId(sellerId).createdAt(sameTime).build()));

        // when
        List<UUID> actual = followJpaRepository.findViewsByMemberId(memberId, PageRequest.of(0, 5))
                .getContent().stream().map(FollowView::sellerId).toList();

        // then
        assertThat(actual).containsExactlyElementsOf(ascending.reversed());
    }

    /** 페이지를 나눠 읽어도 같은 행이 두 번 나오거나 빠지지 않는다. */
    @Test
    void 등록시각이_같아도_페이지를_나눠_읽으면_중복이나_누락이_없다() {
        // given
        UUID memberId = createMember("구매자");
        Instant sameTime = Instant.parse("2026-09-16T00:00:00Z");
        for (int i = 0; i < 6; i++) {
            followJpaRepository.save(FollowJpaEntity.builder()
                    .memberId(memberId).sellerId(createMember("판매자" + i)).createdAt(sameTime).build());
        }

        // when
        List<UUID> paged = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            followJpaRepository.findViewsByMemberId(memberId, PageRequest.of(page, 2))
                    .forEach(v -> paged.add(v.sellerId()));
        }

        // then
        assertThat(paged).hasSize(6).doesNotHaveDuplicates();
    }
}
