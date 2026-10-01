package com.fundit.order.presentation.dto;

import com.fundit.order.domain.funding.ShippingAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ShippingAddressRequest(
        // 보이는 문자가 하나는 있어야 한다(#218, QA-064) — member AddressPayload.RECIPIENT_NAME_PATTERN과 같게 유지
        @NotBlank @Pattern(regexp = "(?s).*[^\\p{IsWhite_Space}\\p{Cc}\\p{Cf}"
                + "\\u034F\\u115F\\u1160\\u17B4\\u17B5\\u180B-\\u180F\\u2065\\u3164\\uFE00-\\uFE0F\\uFFA0\\uFFF0-\\uFFF8"
                + "\\x{E0000}-\\x{E0FFF}\\u2800].*", message = "받는 분 이름을 입력해 주세요.")
        String recipientName,
        @NotBlank @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "연락처는 휴대폰 번호 형식이어야 합니다.")
        String phoneNumber,
        @NotBlank String zipcode,
        @NotBlank String addressLine1,
        String addressLine2
) {

    public ShippingAddress toDomain() {
        return new ShippingAddress(recipientName, phoneNumber, zipcode, addressLine1, addressLine2);
    }
}
