package com.fundit.member.infrastructure.persistence.wish;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WishJpaRepository.insertIgnoringConflict()의 ON CONFLICT DO NOTHING이 실제로 중복 삽입을
 * 에러 없이 무시하는지는 실행해보기 전까진 확신할 수 없어 검증한다(찜 등록 idempotent
 * 요구사항, member-service CLAUDE.md 핵심 설계 결정). @Modifying 커스텀 쿼리는
 * SimpleJpaRepository 기본 CRUD와 달리 자동으로 트랜잭션이 걸리지 않아(auth-service
 * RefreshTokenJpaRepository에서 확인된 동일 이슈) 테스트 클래스에 @Transactional을 둔다.
 *
 * internal-api.key를 @TestPropertySource로 고정하는 이유: 공통 application.yml의
 * spring.profiles.active=local이 CI 체크아웃 트리에 없는 application-local.yml을 가리켜서,
 * 이 값을 채워줄 프로필 파일이 없으면 전체 컨텍스트 로딩이 PlaceholderResolutionException으로
 * 실패한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
@Transactional
class WishJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private WishJpaRepository wishJpaRepository;
    @Autowired
    private MemberJpaRepository memberJpaRepository;

    private UUID createMember() {
        return memberJpaRepository.save(MemberJpaEntity.builder()
                .id(UUID.randomUUID()).name("홍길동").phoneNumber("01012345678").build()).getId();
    }

    @Test
    void 같은_회원이_같은_프로젝트를_두번_찜해도_한_행만_남는다() {
        // given
        UUID memberId = createMember();

        // when
        wishJpaRepository.insertIgnoringConflict(memberId, 1L);
        wishJpaRepository.insertIgnoringConflict(memberId, 1L);

        // then
        assertThat(wishJpaRepository.findByMemberId(memberId, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void 찜하지_않은_프로젝트를_해제해도_예외가_발생하지_않는다() {
        // given
        UUID memberId = createMember();

        // when & then (예외 없이 완료되면 통과)
        wishJpaRepository.deleteByMemberIdAndProjectId(memberId, 999L);
    }

    @Test
    void 찜을_해제하면_목록에서_사라진다() {
        // given
        UUID memberId = createMember();
        wishJpaRepository.insertIgnoringConflict(memberId, 2L);

        // when
        wishJpaRepository.deleteByMemberIdAndProjectId(memberId, 2L);

        // then
        assertThat(wishJpaRepository.findByMemberId(memberId, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(0);
    }
}
