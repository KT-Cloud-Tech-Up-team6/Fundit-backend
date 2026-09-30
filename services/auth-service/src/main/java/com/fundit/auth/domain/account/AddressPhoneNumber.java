package com.fundit.auth.domain.account;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 가입 배송지(선택 입력)의 연락처 형식을 가려낸다(#209, QA-061~063).
 * member도 같은 형식으로 막지만, member의 400은 auth에서 503이 되고 그 시점엔 1회용 토큰이 이미 소비돼 있다.
 * 그래서 가입 맨 앞에서 한 번 더 본다. 정규식은 member {@code AddressPayload.PHONE_PATTERN}과 같게 유지한다.
 */
public final class AddressPhoneNumber {

    /** 휴대폰 번호, 하이픈 선택. */
    private static final Pattern PHONE = Pattern.compile("^01[016789]-?\\d{3,4}-?\\d{4}$");

    private AddressPhoneNumber() {
    }

    /**
     * 주소 없음(null 또는 값이 전부 null)이면 false — member {@code CompleteAddressValidator}와 같은 기준이다.
     * 값이 하나라도 있으면 연락처는 필수다. 없거나 빈 값이어도 member가 부분 입력으로 거절해 503이 되기 때문이다.
     */
    public static boolean isInvalid(Map<String, Object> address) {
        if (address == null || address.values().stream().allMatch(Objects::isNull)) {
            return false;
        }
        return !(address.get("phoneNumber") instanceof String s && PHONE.matcher(s).matches());
    }
}
