package com.fundit.member.application.member;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

public class CompleteAddressValidator implements ConstraintValidator<CompleteAddress, MemberSignupService.AddressPayload> {

    private static final MemberSignupService.AddressPayload EMPTY =
            new MemberSignupService.AddressPayload(null, null, null, null, null, null);

    @Override
    public boolean isValid(MemberSignupService.AddressPayload address, ConstraintValidatorContext context) {
        if (address == null || address.equals(EMPTY)) {
            return true;
        }

        return StringUtils.hasText(address.recipientName())
                && StringUtils.hasText(address.phoneNumber())
                && StringUtils.hasText(address.zipcode())
                && StringUtils.hasText(address.addressLine1());
    }
}
