package com.fundit.member.application.member;

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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberSignupServiceUnitTest {

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
    void 필수약관에_모두_동의하면_회원과_약관동의_이력을_저장한다() {
        // given
        UUID accountId = UUID.randomUUID();
        when(memberJpaRepository.existsById(accountId)).thenReturn(false);
        when(memberJpaRepository.saveAndFlush(any(MemberJpaEntity.class))).thenAnswer(invocation -> {
            MemberJpaEntity entity = invocation.getArgument(0);
            return MemberJpaEntity.builder()
                    .id(entity.getId()).name(entity.getName()).phoneNumber(entity.getPhoneNumber())
                    .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        });

        // when
        MemberSignupService.SignupResult result = memberSignupService.signup(new MemberSignupService.SignupCommand(
                accountId, "홍길동", "01012345678",
                List.of("SERVICE_USE", "PRIVACY", "AGE_OVER_14"), null));

        // then
        assertThat(result.memberId()).isEqualTo(accountId);
        verify(termsAgreementJpaRepository).saveAll(any());
        verify(addressJpaRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void 배송지_정보가_있으면_함께_저장한다() {
        // given
        UUID accountId = UUID.randomUUID();
        when(memberJpaRepository.existsById(accountId)).thenReturn(false);
        when(memberJpaRepository.saveAndFlush(any(MemberJpaEntity.class))).thenAnswer(invocation -> {
            MemberJpaEntity entity = invocation.getArgument(0);
            return MemberJpaEntity.builder()
                    .id(entity.getId()).name(entity.getName()).phoneNumber(entity.getPhoneNumber())
                    .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        });
        MemberSignupService.AddressPayload address = new MemberSignupService.AddressPayload(
                "홍길동", "01012345678", "12345", "테헤란로 1", null, true);

        // when
        memberSignupService.signup(new MemberSignupService.SignupCommand(
                accountId, "홍길동", "01012345678", List.of("SERVICE_USE", "PRIVACY", "AGE_OVER_14"), address));

        // then
        verify(addressJpaRepository).save(any());
    }

    @Test
    void 주소에_recipientName이_없으면_저장하지_않는다() {
        // given
        UUID accountId = UUID.randomUUID();
        when(memberJpaRepository.existsById(accountId)).thenReturn(false);
        when(memberJpaRepository.saveAndFlush(any(MemberJpaEntity.class))).thenAnswer(invocation -> {
            MemberJpaEntity entity = invocation.getArgument(0);
            return MemberJpaEntity.builder()
                    .id(entity.getId()).name(entity.getName()).phoneNumber(entity.getPhoneNumber())
                    .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        });
        MemberSignupService.AddressPayload emptyAddress =
                new MemberSignupService.AddressPayload(null, null, null, null, null, null);

        // when
        memberSignupService.signup(new MemberSignupService.SignupCommand(
                accountId, "홍길동", "01012345678", List.of("SERVICE_USE", "PRIVACY", "AGE_OVER_14"), emptyAddress));

        // then
        verify(addressJpaRepository, org.mockito.Mockito.never()).save(any());
    }
}
