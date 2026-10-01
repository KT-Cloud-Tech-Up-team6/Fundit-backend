package com.fundit.auth.domain.account;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 가입 배송지(선택 입력)의 받는 사람 이름에 보이는 문자가 있는지 가려낸다(#218, QA-064).
 * member도 같은 집합으로 막지만, member의 400은 auth에서 503이 되고 그 시점엔 1회용 토큰이 이미 소비돼 있다.
 * 그래서 가입 맨 앞에서 한 번 더 본다. 정규식은 member {@code AddressPayload.RECIPIENT_NAME_PATTERN}과 같게 유지한다.
 */
public final class AddressRecipientName {

    /** 공백·제어(Cc)·서식(Cf)·Default Ignorable·U+2800이 아닌 문자가 하나라도 있으면 통과. */
    private static final Pattern VISIBLE = Pattern.compile("(?s).*[^\\p{IsWhite_Space}\\p{Cc}\\p{Cf}"
            + "\\u034F\\u115F\\u1160\\u17B4\\u17B5\\u180B-\\u180F\\u2065\\u3164\\uFE00-\\uFE0F\\uFFA0\\uFFF0-\\uFFF8"
            + "\\x{E0000}-\\x{E0FFF}\\u2800].*");

    private AddressRecipientName() {
    }

    /**
     * 주소 없음(null 또는 값이 전부 null)이면 false — member {@code CompleteAddressValidator}와 같은 기준이다.
     * 값이 하나라도 있으면 받는 사람은 필수다. 없거나 빈 값이어도 member가 부분 입력으로 거절해 503이 되기 때문이다.
     */
    public static boolean isInvalid(Map<String, Object> address) {
        if (address == null || address.values().stream().allMatch(Objects::isNull)) {
            return false;
        }
        return !(address.get("recipientName") instanceof String s && VISIBLE.matcher(s).matches());
    }
}
