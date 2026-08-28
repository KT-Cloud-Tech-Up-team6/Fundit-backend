package com.fundit.common.error;

/**
 * 모든 도메인 에러 코드가 구현해야 하는 공통 인터페이스.
 * 서비스별 에러 코드는 이 인터페이스를 구현한 enum으로 정의한다.
 *
 * 사용 예:
 * <pre>{@code
 * public enum OrderErrorCode implements ErrorCode {
 *     PAID_ALREADY(409, "이미 결제되었습니다");
 *
 *     private final int httpStatus;
 *     private final String message;
 *
 *     OrderErrorCode(int httpStatus, String message) {
 *         this.httpStatus = httpStatus;
 *         this.message = message;
 *     }
 *
 *     @Override
 *     public int getHttpStatus() { return httpStatus; }
 *
 *     @Override
 *     public String getMessage() { return message; }
 * }
 * }</pre>
 *
 * 등록된 ErrorCode가 아닌 임의 문자열 코드/상태코드를 직접 반환하지 않는다.
 * 공통 에러 코드(CommonErrorCode)는 새 도메인 코드로 재정의하지 말고 그대로 사용한다.
 */
public interface ErrorCode {

    /**
     * enum 구현체는 별도 구현 없이 자동으로 이 메서드를 갖는다 (java.lang.Enum#name()).
     * enum이 아닌 구현체를 만들 경우에는 직접 구현해야 한다.
     */
    String name();

    /**
     * 에러 코드 문자열. 기본적으로 enum 상수 이름을 그대로 사용한다.
     * 예: OrderErrorCode.SEAT_ALREADY_RESERVED -> "SEAT_ALREADY_RESERVED"
     */
    default String getCode() {
        return name();
    }

    /**
     * 이 에러에 대응하는 HTTP 상태 코드 (예: 404, 409).
     * int로 두어 modules:common이 특정 웹 프레임워크(Spring 등)에 의존하지 않도록 한다.
     * Spring의 HttpStatus를 쓰고 싶다면 이 반환 타입을 교체하면 된다.
     */
    int getHttpStatus();

    /** 클라이언트에 노출할 에러 메시지 */
    String getMessage();
}