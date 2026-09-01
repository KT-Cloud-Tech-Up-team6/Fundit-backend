package com.fundit.project.support;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 예외 테스트가 "어떤 ErrorCode로 실패했는지"까지 확인하도록 강제한다.
 * BusinessException만 확인하고 넘어가면 400과 423을 구분하지 못한 채 통과해버린다.
 */
public final class BusinessExceptionAssertions {

    private BusinessExceptionAssertions() {
    }

    public static void assertBusinessException(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(thrown -> ((BusinessException) thrown).getErrorCode())
                .isEqualTo(expected);
    }
}
