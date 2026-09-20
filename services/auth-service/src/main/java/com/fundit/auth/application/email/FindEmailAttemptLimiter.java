package com.fundit.auth.application.email;

/**
 * 이메일 찾기 1단계의 시도 제한(아웃바운드 포트).
 *
 * <p><b>왜 필요한가</b>: 1단계는 본인인증 <b>전</b>이라 이름·전화번호만으로 마스킹 이메일을
 * 돌려준다. 제한이 없으면 번호를 바꿔가며 넣어 가입 여부를 훑을 수 있다
 * (member-service가 "번호 → 계정" 조회를 거부한 이유와 같은 위험이다).
 */
public interface FindEmailAttemptLimiter {

    /**
     * 시도를 1 올리고 한도를 넘었는지 본다.
     *
     * @return 한도를 넘었으면 {@code true} — 호출자는 조회하지 않고 429로 끊는다
     */
    boolean exceeded(String phoneNumber);
}
