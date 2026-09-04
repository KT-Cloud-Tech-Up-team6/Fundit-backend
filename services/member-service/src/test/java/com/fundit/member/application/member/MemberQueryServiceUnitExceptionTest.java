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
}
