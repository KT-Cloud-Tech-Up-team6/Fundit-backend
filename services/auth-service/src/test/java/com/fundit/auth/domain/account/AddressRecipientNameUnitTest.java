package com.fundit.auth.domain.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AddressRecipientNameUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {"ㅤ", "​", "⠀", " ", "﻿", "\u0001", " ㅤ​⠀ ﻿\t", ""})
    void 보이지_않는_문자뿐이면_잘못된_받는_사람이다(String recipientName) {
        // given (#218, QA-064)
        Map<String, Object> address = Map.of("recipientName", recipientName, "phoneNumber", "01012345678");

        // when
        boolean invalid = AddressRecipientName.isInvalid(address);

        // then
        assertThat(invalid).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"홍길동", "홍​길동", "ㅤ홍", "John Doe"})
    void 보이는_문자가_하나라도_있으면_통과한다(String recipientName) {
        // given — 이름 중간에 섞인 문자는 다듬지 않고 그대로 둔다
        Map<String, Object> address = Map.of("recipientName", recipientName, "phoneNumber", "01012345678");

        // when
        boolean invalid = AddressRecipientName.isInvalid(address);

        // then
        assertThat(invalid).isFalse();
    }

    @Test
    void 주소를_넣지_않았으면_통과한다() {
        // given — 가입 배송지는 선택 입력이다
        Map<String, Object> nullName = new HashMap<>();
        nullName.put("recipientName", null);

        // when & then
        assertThat(AddressRecipientName.isInvalid(null)).isFalse();
        assertThat(AddressRecipientName.isInvalid(Map.of())).isFalse();
        assertThat(AddressRecipientName.isInvalid(nullName)).isFalse();
    }

    @Test
    void 주소를_넣었는데_받는_사람이_없거나_문자열이_아니면_잘못된_받는_사람이다() {
        // given — member가 부분 입력으로 거절하면 503이 되고 토큰도 이미 소비된 뒤다
        Map<String, Object> nullName = new HashMap<>(Map.of("phoneNumber", "01012345678"));
        nullName.put("recipientName", null);

        // when & then
        assertThat(AddressRecipientName.isInvalid(Map.of("phoneNumber", "01012345678"))).isTrue();
        assertThat(AddressRecipientName.isInvalid(nullName)).isTrue();
        assertThat(AddressRecipientName.isInvalid(Map.of("recipientName", 123))).isTrue();
    }
}
