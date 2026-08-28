package com.fundit.common.webmvc.error;

import com.fundit.common.error.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 공통 예외 처리 로직. 각 서비스는 이걸 상속한 얇은 클래스에 @RestControllerAdvice만 붙이면 된다.
 *
 * <pre>{@code
 * @RestControllerAdvice
 * public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
 * }
 * }</pre>
 *
 * @RestControllerAdvice를 이 클래스에 직접 붙이지 않은 이유: 서비스마다 컴포넌트 스캔 범위가
 * 다를 수 있어서, 이 클래스가 자동으로 안 잡힐 위험이 있다. 각 서비스에 얇은 구현체를 두면
 * 그 문제를 피할 수 있다.
 *
 * 이 클래스가 modules:common이 아니라 modules:common-webmvc에 있는 이유:
 * 상속하는 org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler는
 * Servlet(블로킹) 기반 Spring MVC 전용이다. WebFlux는 시그니처가 다른 별도 클래스
 * (org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler,
 * 리턴 타입도 Mono<ResponseEntity<Object>>)를 써야 해서 이 클래스와 공존할 수 없다.
 * 그래서 이 클래스를 modules:common에 두면 WebFlux 기반 서비스까지 강제로 spring-webmvc를
 * 끌고 가게 된다. Servlet 기반 서비스만 modules:common-webmvc에 의존하고,
 * modules:common 자체는 웹 프레임워크에 대해 아무것도 모른다.
 * WebFlux 서비스가 생기면 그때 modules:common-webflux를 같은 패턴으로 따로 만들 것.
 *
 * Spring Boot 4.1.1 / Spring Framework 7 기준. handleExceptionInternal() 하나만 오버라이드하면
 * 검증 실패(MethodArgumentNotValidException), 잘못된 JSON Body(HttpMessageNotReadableException),
 * 지원하지 않는 HTTP 메소드(HttpRequestMethodNotSupportedException) 등 Spring MVC가 인식하는
 * 표준 예외 40여 종이 전부 여기로 모여서, 우리 팀 표준 {code, message, detail} 포맷으로
 * 한 번에 통일해서 응답한다.
 *
 * 주의(이름 충돌): Spring 6+부터 프레임워크 자체에 org.springframework.web.ErrorResponse
 * 인터페이스가 생겼고(RFC 9457 ProblemDetail 용, MethodArgumentNotValidException 등이 구현함),
 * 이름이 우리 ErrorResponse 레코드(com.fundit.common.error.ErrorResponse)와 완전히 같다.
 * 이 파일에서 org.springframework.web.ErrorResponse를 import하지 말 것.
 */
public abstract class AbstractGlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractGlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: code={}, message={}", errorCode.getCode(), e.getMessage());
        return respond(errorCode);
    }

    @ExceptionHandler(DependencyFailureException.class)
    public ResponseEntity<ErrorResponse> handleDependencyFailureException(DependencyFailureException e) {
        ErrorCode errorCode = e.getErrorCode();
        // 외부 연동 실패는 원인 파악이 필요해서 cause를 포함해 error 레벨로 로깅한다.
        // (응답 바디에는 스택트레이스를 노출하지 않는다 — security.md S10)
        log.error("DependencyFailureException: code={}, message={}", errorCode.getCode(), e.getMessage(), e.getCause());
        return respond(errorCode);
    }

    // BusinessException/DependencyFailureException도 아니고, Spring MVC가 인식하는
    // 표준 예외(아래 handleExceptionInternal 경로)도 아닌 진짜 예상 못한 예외에 대한 최후 방어선.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("Unhandled exception", e);
        return respond(CommonErrorCode.INTERNAL_ERROR);
    }

    /**
     * ResponseEntityExceptionHandler가 인식하는 Spring MVC 표준 예외가 최종적으로
     * 전부 여기로 모인다. 여기서 우리 표준 {code, message, detail} 포맷으로 통일해서 응답한다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ErrorCode errorCode = resolveErrorCode(statusCode);
        Object detail = (ex instanceof MethodArgumentNotValidException manve)
                ? extractFieldErrors(manve)
                : null;

        log.warn("MVC exception handled: type={}, status={}, message={}",
                ex.getClass().getSimpleName(), statusCode, ex.getMessage());

        return ResponseEntity
                .status(statusCode)
                .headers(headers)
                .body(ErrorResponse.of(errorCode, detail));
    }

    /**
     * HTTP 상태코드로 CommonErrorCode를 역으로 찾는다.
     * 주의: 401(UNAUTHORIZED/TOKEN_EXPIRED/TOKEN_INVALID), 503(SERVICE_UNAVAILABLE/DEPENDENCY_FAILURE)처럼
     * 같은 상태코드를 쓰는 코드가 여럿이면 CommonErrorCode에 선언된 순서상 먼저 나오는 걸 반환한다.
     * 더 정확한 코드가 필요하면 이 메서드를 오버라이드하거나, 해당 상황에서 직접 BusinessException을 던지면 된다.
     */
    private ErrorCode resolveErrorCode(HttpStatusCode statusCode) {
        return Arrays.stream(CommonErrorCode.values())
                .filter(code -> code.getHttpStatus() == statusCode.value())
                .findFirst()
                .map(code -> (ErrorCode) code)
                .orElse(CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode));
    }

    private List<Map<String, String>> extractFieldErrors(MethodArgumentNotValidException e) {
        return e.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldErrorDetail)
                .toList();
    }

    private Map<String, String> toFieldErrorDetail(FieldError fieldError) {
        return Map.of(
                "field", fieldError.getField(),
                "reason", fieldError.getDefaultMessage() == null ? "" : fieldError.getDefaultMessage()
        );
    }
}