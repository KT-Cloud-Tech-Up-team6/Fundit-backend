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
}
