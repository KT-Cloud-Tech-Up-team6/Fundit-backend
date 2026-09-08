package com.fundit.common.webmvc.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 게이트웨이가 검증·주입한 로그인 사용자를 컨트롤러 파라미터로 주입받기 위한 마커.
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public MemberMeResponse getMe(@LoginUser CurrentUser user) {
 *     return service.getMe(user.id());
 * }
 * }</pre>
 *
 * 각 서비스가 헤더를 직접 파싱하지 않게 하려는 게 목적이다 — 헤더 이름이 바뀌었을 때
 * 고쳐야 할 곳이 이 모듈 하나로 유지된다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}
