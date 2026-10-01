package com.fundit.auth.domain.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AddressPhoneNumberUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {"abc", "010123", "00000000000", "02-123-4567", "010-1234-56789"})
    void 휴대폰_번호_형식이_아니면_잘못된_연락처다(String phoneNumber) {
        // given
        Map<String, Object> address = Map.of("phoneNumber", phoneNumber);

        // when
        boolean invalid = AddressPhoneNumber.isInvalid(address);

        // then
        assertThat(invalid).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"010-1234-5678", "01012345678", "011-123-4567", "0191234567"})
    void 하이픈이_있든_없든_휴대폰_번호면_통과한다(String phoneNumber) {
        // given
        Map<String, Object> address = Map.of("phoneNumber", phoneNumber);

        // when
        boolean invalid = AddressPhoneNumber.isInvalid(address);

        // then
        assertThat(invalid).isFalse();
    }

    @Test
    void 주소를_넣지_않았으면_통과한다() {
        // given — 가입 배송지는 선택 입력이다
        Map<String, Object> nullPhone = new HashMap<>();
        nullPhone.put("phoneNumber", null);

        // when & then
        assertThat(AddressPhoneNumber.isInvalid(null)).isFalse();
        assertThat(AddressPhoneNumber.isInvalid(Map.of())).isFalse();
        assertThat(AddressPhoneNumber.isInvalid(nullPhone)).isFalse();
    }

    @Test
    void 주소를_넣었는데_연락처가_없거나_비어_있으면_잘못된_연락처다() {
        // given — member가 부분 입력으로 거절하면 503이 되고 토큰도 이미 소비된 뒤다
        Map<String, Object> nullPhone = new HashMap<>(Map.of("recipientName", "홍길동"));
        nullPhone.put("phoneNumber", null);

        // when & then
        assertThat(AddressPhoneNumber.isInvalid(Map.of("recipientName", "홍길동", "zipcode", "12345"))).isTrue();
        assertThat(AddressPhoneNumber.isInvalid(nullPhone)).isTrue();
        assertThat(AddressPhoneNumber.isInvalid(Map.of("recipientName", "홍길동", "phoneNumber", ""))).isTrue();
        assertThat(AddressPhoneNumber.isInvalid(Map.of("phoneNumber", " "))).isTrue();
    }

    @Test
    void 연락처가_문자열이_아니면_잘못된_연락처다() {
        // given
        Map<String, Object> address = Map.of("phoneNumber", 1012345678L);

        // when
        boolean invalid = AddressPhoneNumber.isInvalid(address);

        // then
        assertThat(invalid).isTrue();
    }
}
