package com.fundit.fulfillment.presentation;

import com.fundit.common.webmvc.error.AbstractGlobalExceptionHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerUnitTest {

    @Test
    void 공통_예외_핸들러를_상속한다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertThat(handler).isInstanceOf(AbstractGlobalExceptionHandler.class);
    }
}
