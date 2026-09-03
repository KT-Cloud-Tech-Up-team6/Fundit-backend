package com.fundit.auth.presentation.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호 복잡도 규칙(AuthFunctionalSpec.md AUTH-007에 구체 기준 없어 가정치로 확정):
 * 최소 8자 + {대문자,소문자,숫자,특수문자} 중 3종류 이상.
 */
@Constraint(validatedBy = PasswordComplexityValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "비밀번호는 최소 8자 이상이며 대문자/소문자/숫자/특수문자 중 3종류 이상을 포함해야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
