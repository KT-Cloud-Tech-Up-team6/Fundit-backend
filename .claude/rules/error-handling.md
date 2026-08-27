---
paths:
  - "**/application/**"
  - "**/domain/exception/**"
  - "**/infrastructure/**"
---

# 에러 코드 / 예외 처리 규칙

## ErrorCode
- `modules:common`의 `ErrorCode` 인터페이스(`getCode()`, `getHttpStatus()`, `getMessage()`)를 각 서비스가 자기 도메인 전용 enum으로 구현
- 예: `OrderErrorCode implements ErrorCode { SEAT_ALREADY_RESERVED(409, "이미 선점된 좌석입니다.") }`
- **등록된 `ErrorCode`가 아닌 임의 문자열 코드/상태코드를 직접 반환하지 않는다**
- 공통 에러 코드(`INVALID_INPUT`, `UNAUTHORIZED`, `NOT_FOUND` 등)는 새 도메인 코드로 재정의하지 말고 그대로 사용

## 예외 던지기
- 비즈니스 규칙 위반은 `BusinessException(ErrorCode)`를 던진다 (application/domain 계층)
- 외부 연동(PG, AI, 스트리밍 등) 실패는 `DependencyFailureException` 계열로 감싸서 던진다 (infrastructure 계층)
- 컨트롤러에서 직접 `try-catch`로 응답을 조립하지 않는다 — `GlobalExceptionHandler`(`@RestControllerAdvice`)가 일괄 처리

## 로깅
- 예외 스택트레이스를 사용자 응답에 노출하지 않는다
- 로그에 비밀번호·토큰·개인정보를 남기지 않는다 (`security.md` S10 참고)