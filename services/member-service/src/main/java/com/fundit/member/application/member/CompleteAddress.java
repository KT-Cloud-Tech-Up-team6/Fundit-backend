package com.fundit.member.application.member;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * auth-service가 주소 미입력 시 빈 객체({})를 보내는 것은 허용하되, 필드가 하나라도
 * 채워져 있으면 recipientName/phoneNumber/zipcode/addressLine1은 전부 있어야 한다.
 */
@Constraint(validatedBy = CompleteAddressValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CompleteAddress {

    String message() default "주소를 입력하려면 recipientName, phoneNumber, zipcode, addressLine1이 모두 필요합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
