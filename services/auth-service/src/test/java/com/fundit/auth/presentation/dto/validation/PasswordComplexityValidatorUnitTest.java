package com.fundit.auth.presentation.dto.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordComplexityValidatorUnitTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    record TestForm(@NotBlank @ValidPassword String password) {
    }

    @Test
    void 대문자_소문자_숫자를_포함한_8자_이상이면_유효하다() {
        assertThat(validator.validate(new TestForm("Abcdefg1"))).isEmpty();
    }

    @Test
    void 길이가_8자_미만이면_무효하다() {
        assertThat(validator.validate(new TestForm("Ab1!"))).isNotEmpty();
    }

    @Test
    void 문자종류가_2종류_이하면_무효하다() {
        assertThat(validator.validate(new TestForm("abcdefgh"))).isNotEmpty();
    }

    @Test
    void 특수문자를_포함해_3종류_이상이면_유효하다() {
        assertThat(validator.validate(new TestForm("Abcdefg!"))).isEmpty();
    }
}
