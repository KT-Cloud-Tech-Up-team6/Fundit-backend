package com.fundit.project.presentation;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.ErrorCode;
import com.fundit.common.error.ErrorResponse;
import com.fundit.common.webmvc.error.AbstractGlobalExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {

    /**
     * 공통 핸들러는 ErrorCode의 기본 메시지만 응답한다. 이 서비스에는 "검수 요청에 필요한 항목이
     * 누락되었습니다: title, rewards"처럼 상황별 메시지를 담아 던지는 지점이 있어
     * (ProjectDomainApiSpec의 "누락 필드 목록 반환" 요구), 기본 메시지와 다를 때만 detail에 실어 보낸다.
     */
    @Override
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (Objects.equals(errorCode.getMessage(), e.getMessage())) {
            return super.handleBusinessException(e);
        }
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, e.getMessage()));
    }
}
