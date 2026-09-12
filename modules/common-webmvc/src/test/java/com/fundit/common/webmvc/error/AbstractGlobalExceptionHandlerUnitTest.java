package com.fundit.common.webmvc.error;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스프링 컨텍스트 없이 핸들러 메서드를 직접 부른다 — 이 레포에서 컨텍스트를 띄우는 테스트는
 * DB가 없는 CI에서 이미 두 번 깨졌다.
 */
class AbstractGlobalExceptionHandlerUnitTest {

    private final AbstractGlobalExceptionHandler handler = new AbstractGlobalExceptionHandler() {
    };

    @Test
    void 상황별_메시지를_넘기면_기본_메시지가_아니라_그것이_응답에_실린다() {
        // given — BusinessException(errorCode, message)가 "기본 메시지 대신"이라고 문서화돼 있는데
        // 실제로는 로그에만 남고 응답엔 enum의 기본 메시지가 나가고 있었다
        var exception = new BusinessException(CommonErrorCode.CONFLICT, "이미 생성된 회원 프로필입니다.");

        // when
        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(exception);

        // then
        assertThat(response.getBody().message()).isEqualTo("이미 생성된 회원 프로필입니다.");
        assertThat(response.getBody().code()).isEqualTo(CommonErrorCode.CONFLICT.getCode());
    }

    @Test
    void 메시지를_안_넘기면_기본_메시지를_쓴다() {
        // given
        var exception = new BusinessException(CommonErrorCode.NOT_FOUND);

        // when
        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(exception);

        // then
        assertThat(response.getBody().message()).isEqualTo(CommonErrorCode.NOT_FOUND.getMessage());
        assertThat(response.getBody().detail()).isNull();
    }

    @Test
    void detail을_넘기면_응답에_그대로_실린다() {
        // given — 클라이언트가 메시지 문장을 파싱하지 않고 분기할 수 있어야 한다
        var exception = new BusinessException(
                CommonErrorCode.CONFLICT, "이미 KAKAO 소셜 로그인 계정이 존재합니다.", Map.of("provider", "KAKAO"));

        // when
        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(exception);

        // then
        assertThat(response.getBody().detail()).isEqualTo(Map.of("provider", "KAKAO"));
    }
}
