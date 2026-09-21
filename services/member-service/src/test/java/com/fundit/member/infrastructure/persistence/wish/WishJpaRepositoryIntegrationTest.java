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
@TestPropertySource(properties = {
        "member.encryption.key=Oz9qy5geAzhDXyHfZdFB3WPwVH8/sx/uD2j5rX5DGkY=",
        "internal-api.key=test-only-internal-api-key"})
@Transactional
class WishJpaRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private WishJpaRepository wishJpaRepository;
    @Autowired
    private MemberJpaRepository memberJpaRepository;
    @Autowired
    private com.fundit.member.infrastructure.persistence.projectsnapshot.ProjectSnapshotJpaRepository projectSnapshotJpaRepository;

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
        assertThat(wishJpaRepository.findViewsByMemberId(memberId, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
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
        assertThat(wishJpaRepository.findViewsByMemberId(memberId, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(0);
    }

    @Test
    void 찜_목록은_프로젝트_스냅샷의_UUID_제목_썸네일을_채우고_없으면_null이다() {
        // given — 10번은 스냅샷이 있고 11번은 아직 이벤트가 오지 않았다
        UUID memberId = createMember();
        UUID publicId = UUID.randomUUID();
        projectSnapshotJpaRepository.upsert(10L, publicId, "에어쿡 프로", "https://img/10.png", 5L);
        wishJpaRepository.insertIgnoringConflict(memberId, 10L);
        wishJpaRepository.insertIgnoringConflict(memberId, 11L);

        // when
        var wishes = wishJpaRepository.findViewsByMemberId(memberId, PageRequest.of(0, 20)).getContent();

        // then — 스냅샷이 없어도 찜 목록에서 빠지지 않는다
        assertThat(wishes).hasSize(2);
        var withSnapshot = wishes.stream().filter(w -> w.projectId().equals(10L)).findFirst().orElseThrow();
        assertThat(withSnapshot.projectPublicId()).isEqualTo(publicId);
        assertThat(withSnapshot.projectTitle()).isEqualTo("에어쿡 프로");
        var withoutSnapshot = wishes.stream().filter(w -> w.projectId().equals(11L)).findFirst().orElseThrow();
        assertThat(withoutSnapshot.projectPublicId()).isNull();
    }

    @Test
    void 스냅샷은_더_최신_버전으로만_갱신된다() {
        // given
        UUID memberId = createMember();
        UUID publicId = UUID.randomUUID();
        projectSnapshotJpaRepository.upsert(20L, publicId, "새 제목", "https://img/new.png", 7L);

        // when — 재전송된 옛 이벤트
        projectSnapshotJpaRepository.upsert(20L, publicId, "옛 제목", "https://img/old.png", 3L);
        wishJpaRepository.insertIgnoringConflict(memberId, 20L);

        // then
        var wish = wishJpaRepository.findViewsByMemberId(memberId, PageRequest.of(0, 20)).getContent().getFirst();
        assertThat(wish.projectTitle()).isEqualTo("새 제목");
    }
}
