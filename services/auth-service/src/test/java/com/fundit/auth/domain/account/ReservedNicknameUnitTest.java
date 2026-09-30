package com.fundit.auth.domain.account;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReservedNicknameUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "판매자", "나", "AI 매니저", "시청자",
            " 판매자 ", "판 매 자", "ai 매니저", "Ai매니저",
            "판​매자", "시﻿청자", "나⁠", // 폭 없는 문자 삽입
            "판 매자",                            // NBSP — isWhitespace는 false라 NFKC 후 빠져야 한다
            "ＡＩ매니저"                                 // 전각
    })
    void 채팅_라벨과_같은_닉네임은_변형해도_예약어다(String nickname) {
        // given - 파라미터

        // when
        boolean reserved = ReservedNickname.isReserved(nickname);

        // then
        assertThat(reserved).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"응원왕", "판매자123", "나는구매자", "AI매니저님"})
    void 정상_닉네임과_부분_일치는_통과한다(String nickname) {
        // given - 파라미터

        // when
        boolean reserved = ReservedNickname.isReserved(nickname);

        // then
        assertThat(reserved).isFalse();
    }
}
