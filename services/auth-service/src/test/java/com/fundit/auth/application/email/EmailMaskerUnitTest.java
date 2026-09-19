package com.fundit.auth.application.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailMaskerUnitTest {

    @ParameterizedTest
    @CsvSource({
            "1234qwer@gmail.com, 1234q***@gmail.com",
            "abcdefg@fundit.com, abcd***@fundit.com",
            "abcde@x.co,         ab***@x.co"
    })
    void 로컬파트_뒤_3자를_가리고_도메인은_그대로_둔다(String email, String expected) {
        // given & when & then — 피그마 기준 규칙이다
        assertThat(EmailMasker.mask(email)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "abcd@x.com, a***@x.com",
            "abc@x.com,  ***@x.com",
            "ab@x.com,   ***@x.com",
            "a@x.com,    ***@x.com"
    })
    void 로컬파트가_짧으면_통째로_가린다(String email, String expected) {
        // given & when & then — 가릴 자리가 없으면 한 글자도 흘리지 않는다
        assertThat(EmailMasker.mask(email)).isEqualTo(expected);
    }

    @Test
    void 도메인에_골뱅이가_여러_개면_마지막을_기준으로_나눈다() {
        // given & when & then — 로컬파트에 따옴표로 @를 넣을 수 있다(RFC 5321)
        assertThat(EmailMasker.mask("ab@cdefg@fundit.com")).isEqualTo("ab@cd***@fundit.com");
    }

    @Test
    void 골뱅이가_없으면_전부_가린다() {
        // given & when & then — 저장 시점에 검증하므로 방어용이다
        assertThat(EmailMasker.mask("not-an-email")).isEqualTo("***");
    }
}
