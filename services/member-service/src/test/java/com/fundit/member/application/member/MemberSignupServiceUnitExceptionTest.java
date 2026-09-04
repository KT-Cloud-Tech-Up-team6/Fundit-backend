package com.fundit.member.application.member;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.infrastructure.persistence.address.AddressJpaRepository;
import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import com.fundit.member.infrastructure.persistence.termsagreement.TermsAgreementJpaRepository;
import com.fundit.member.infrastructure.terms.TermsCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class MemberSignupServiceUnitExceptionTest {

    @Mock
    private MemberJpaRepository memberJpaRepository;
    @Mock
    private TermsAgreementJpaRepository termsAgreementJpaRepository;
    @Mock
    private AddressJpaRepository addressJpaRepository;
    @Spy
    private TermsCatalog termsCatalog = new TermsCatalog();

    @InjectMocks
    private MemberSignupService memberSignupService;

    @Test
    void 이미_생성된_회원이면_예외가_발생한다() {
        // given
        UUID accountId = UUID.randomUUID();
        when(memberJpaRepository.existsById(accountId)).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> memberSignupService.signup(new MemberSignupService.SignupCommand(
                accountId, "홍길동", "01012345678", List.of("SERVICE_USE", "PRIVACY", "AGE_OVER_14"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
        verify(memberJpaRepository, never()).save(any());
    }

    @Test
    void 필수약관에_동의하지_않으면_예외가_발생한다() {
        // given
        UUID accountId = UUID.randomUUID();
        when(memberJpaRepository.existsById(accountId)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> memberSignupService.signup(new MemberSignupService.SignupCommand(
                accountId, "홍길동", "01012345678", List.of("MARKETING"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
        verify(memberJpaRepository, never()).save(any());
    }

    @Test
    void existsById_통과후_동시가입으로_유니크제약이_깨지면_409로_변환된다() {
        // given: existsById 체크와 save() 사이에 동시 요청이 끼어든 상황(TOCTOU)을 흉내낸다.
        UUID accountId = UUID.randomUUID();
        when(memberJpaRepository.existsById(accountId)).thenReturn(false);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"))
                .when(memberJpaRepository).save(any(MemberJpaEntity.class));

        // when & then
        assertThatThrownBy(() -> memberSignupService.signup(new MemberSignupService.SignupCommand(
                accountId, "홍길동", "01012345678", List.of("SERVICE_USE", "PRIVACY", "AGE_OVER_14"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
    }
}
