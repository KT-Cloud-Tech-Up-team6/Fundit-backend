package com.fundit.member.application.member;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberQueryServiceUnitExceptionTest {

    @Mock
    private MemberJpaRepository memberJpaRepository;

    @InjectMocks
    private MemberQueryService memberQueryService;

    @Test
    void 존재하지_않는_회원이면_예외가_발생한다() {
        // given
        UUID accountId = UUID.randomUUID();
        when(memberJpaRepository.findByIdAndDeletedAtIsNull(accountId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberQueryService.getMe(accountId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 닉네임_일괄_조회는_상한을_넘으면_400이다() {
        // given — 상한이 없으면 id 수천 개로 회원 닉네임을 한 번에 긁어갈 수 있다
        java.util.List<UUID> ids = java.util.stream.Stream.generate(UUID::randomUUID)
                .limit(MemberQueryService.MAX_NICKNAME_LOOKUP + 1).toList();

        // when & then
        assertThatThrownBy(() -> memberQueryService.findNicknames(ids))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }
}
