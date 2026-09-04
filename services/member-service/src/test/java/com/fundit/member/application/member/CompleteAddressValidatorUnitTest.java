package com.fundit.member.application.member;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompleteAddressValidatorUnitTest {

    private final CompleteAddressValidator validator = new CompleteAddressValidator();

    @Test
    void null이면_유효하다() {
        // when & then
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void 모든_필드가_없으면_유효하다() {
        // given
        MemberSignupService.AddressPayload address =
                new MemberSignupService.AddressPayload(null, null, null, null, null, null);

        // when & then
        assertThat(validator.isValid(address, null)).isTrue();
    }

    @Test
    void 필수_필드가_모두_있으면_유효하다() {
        // given
        MemberSignupService.AddressPayload address = new MemberSignupService.AddressPayload(
                "홍길동", "01012345678", "12345", "테헤란로 1", null, null);

        // when & then
        assertThat(validator.isValid(address, null)).isTrue();
    }

    @Test
    void 수령인_이름만_있으면_유효하지_않다() {
        // given
        MemberSignupService.AddressPayload address =
                new MemberSignupService.AddressPayload("홍길동", null, null, null, null, null);

        // when & then
        assertThat(validator.isValid(address, null)).isFalse();
    }

    @Test
    void 연락처만_있으면_유효하지_않다() {
        // given
        MemberSignupService.AddressPayload address =
                new MemberSignupService.AddressPayload(null, "01012345678", null, null, null, null);

        // when & then
        assertThat(validator.isValid(address, null)).isFalse();
    }

    @Test
    void 수령인_이름과_연락처만_있으면_유효하지_않다() {
        // given
        MemberSignupService.AddressPayload address =
                new MemberSignupService.AddressPayload("홍길동", "01012345678", null, null, null, null);

        // when & then
        assertThat(validator.isValid(address, null)).isFalse();
    }

    @Test
    void 우편번호까지만_있으면_유효하지_않다() {
        // given
        MemberSignupService.AddressPayload address =
                new MemberSignupService.AddressPayload("홍길동", "01012345678", "12345", null, null, null);

        // when & then
        assertThat(validator.isValid(address, null)).isFalse();
    }
}
