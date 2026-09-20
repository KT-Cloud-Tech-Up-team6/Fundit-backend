package com.fundit.auth.application.email;

/**
 * 이메일 찾기(AUTH-009) 1단계에서 화면에 보여줄 마스킹 문자열을 만든다.
 *
 * <p>규칙은 피그마 기준이다 — <b>로컬파트 뒤 3자를 {@code ***}로 가리고 도메인은 그대로</b> 둔다.
 * {@code 1234qwer@gmail.com} → {@code 1234q***@gmail.com}
 *
 * <p>로컬파트가 4자 미만이면 가릴 자리가 없어 <b>통째로 {@code ***}</b>로 만든다 —
 * {@code ab@x.com}을 {@code ***@x.com}이 아니라 {@code ***}만 남기면 도메인 정보까지
 * 잃으므로 도메인은 유지한다. (짧은 주소 규칙은 미확정이라 이 동작이 잠정 기본값이다.)
 */
final class EmailMasker {

    private static final String MASK = "***";
    private static final int MASK_LENGTH = 3;

    private EmailMasker() {
    }

    static String mask(String email) {
        int at = email.lastIndexOf('@');
        if (at <= 0) {
            // @가 없거나 로컬파트가 비면 보여줄 게 없다. 저장 시점에 검증하므로 방어용이다.
            return MASK;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        int visible = local.length() - MASK_LENGTH;
        return (visible < 1 ? MASK : local.substring(0, visible) + MASK) + domain;
    }
}
