package com.fundit.member.application.member;

import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberQueryServiceUnitTest {

    @Mock
    private MemberJpaRepository memberJpaRepository;

    @InjectMocks
    private MemberQueryService memberQueryService;

    @Test
    void 닉네임_일괄_조회는_찾은_회원의_닉네임만_돌려준다() {
        // given — 없는 id·탈퇴 회원은 리포지토리 조건(deletedAt is null)에서 빠진다
        UUID found = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(memberJpaRepository.findAllByIdInAndDeletedAtIsNull(java.util.Set.of(found, missing)))
                .thenReturn(java.util.List.of(MemberJpaEntity.builder()
                        .id(found).name("홍길동").phoneNumber("01012345678").nickname("쓱쓱생활연구소").build()));

        // when
        var result = memberQueryService.findNicknames(java.util.List.of(found, missing));

        // then — 이름·전화번호는 나가지 않는다
        assertThat(result).containsExactly(new MemberQueryService.MemberNickname(found, "쓱쓱생활연구소"));
    }

    @Test
    void 닉네임_일괄_조회에_id가_없으면_조회하지_않고_빈_목록이다() {
        // when
        var result = memberQueryService.findNicknames(java.util.List.of());

        // then
        assertThat(result).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(memberJpaRepository);
    }

    @Test
    void 존재하는_회원이면_프로필을_반환한다() {
        // given
        UUID accountId = UUID.randomUUID();
        MemberJpaEntity entity = MemberJpaEntity.builder()
                .id(accountId).name("홍길동").phoneNumber("01012345678")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(memberJpaRepository.findByIdAndDeletedAtIsNull(accountId)).thenReturn(Optional.of(entity));

        // when
        MemberQueryService.MemberProfile profile = memberQueryService.getMe(accountId);

        // then
        assertThat(profile.memberId()).isEqualTo(accountId);
        assertThat(profile.name()).isEqualTo("홍길동");
    }

    @Test
    void 본인인증_번호가_계정_주인의_번호와_같으면_참을_반환한다() {
        // given
        UUID accountId = UUID.randomUUID();
        MemberJpaEntity entity = MemberJpaEntity.builder()
                .id(accountId).name("홍길동").phoneNumber("01012345678")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(memberJpaRepository.findByIdAndDeletedAtIsNull(accountId)).thenReturn(Optional.of(entity));

        // when
        boolean matches = memberQueryService.phoneMatches(accountId, "01012345678");

        // then
        assertThat(matches).isTrue();
    }

    @Test
    void 번호가_다르면_거짓을_반환한다() {
        // given — 여기가 뚫리면 이메일만 알면 타인 계정에 소셜 로그인을 붙일 수 있다
        UUID accountId = UUID.randomUUID();
        MemberJpaEntity entity = MemberJpaEntity.builder()
                .id(accountId).name("홍길동").phoneNumber("01012345678")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(memberJpaRepository.findByIdAndDeletedAtIsNull(accountId)).thenReturn(Optional.of(entity));

        // when
        boolean matches = memberQueryService.phoneMatches(accountId, "01099998888");

        // then
        assertThat(matches).isFalse();
    }

    @Test
    void 계정이_없으면_예외가_아니라_거짓을_반환한다() {
        // given — "계정 없음"과 "번호 다름"을 호출자에게 구분해 알려줄 이유가 없다
        UUID accountId = UUID.randomUUID();
        when(memberJpaRepository.findByIdAndDeletedAtIsNull(accountId)).thenReturn(Optional.empty());

        // when
        boolean matches = memberQueryService.phoneMatches(accountId, "01012345678");

        // then
        assertThat(matches).isFalse();
    }
}
