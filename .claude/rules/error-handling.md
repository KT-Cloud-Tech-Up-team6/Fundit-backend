---
paths:
  - "**/application/**"
  - "**/domain/exception/**"
  - "**/infrastructure/**"
  - "**/modules/common/**"
  - "**/modules/common-webmvc/**"
---

# 에러 코드 / 예외 처리 규칙

## ErrorCode
- `modules:common`의 `ErrorCode` 인터페이스(`getCode()`, `getHttpStatus()`, `getMessage()`)를 각 서비스가 자기 도메인 전용 enum으로 구현
- 예: `OrderErrorCode implements ErrorCode { SEAT_ALREADY_RESERVED(409, "이미 선점된 좌석입니다.") }`
- `getCode()`는 기본적으로 enum 상수 이름(`name()`)을 그대로 코드로 사용한다 — 별도 code 필드를 두지 않는다
- httpStatus/message 필드에는 Lombok `@Getter`를 사용해도 된다 (`persistence-convention.md`와 동일 원칙 — 프레임워크 종속이 아니므로 허용)
- **등록된 `ErrorCode`가 아닌 임의 문자열 코드/상태코드를 직접 반환하지 않는다**
- 공통 에러 코드는 `modules:common`의 `CommonErrorCode` enum에 이미 정의되어 있다(`INVALID_INPUT`, `UNAUTHORIZED`, `NOT_FOUND` 등 — 전체 목록은 해당 파일 참고) — 새 도메인 코드로 재정의하지 말고 그대로 사용
- 에러 응답 바디는 `modules:common`의 `ErrorResponse` 레코드(`{code, message, detail}`)로 생성한다 — `ErrorResponse.of(ErrorCode)` 또는 필드별 상세가 필요하면 `ErrorResponse.of(ErrorCode, detail)`
- 서비스 내부 도메인 ErrorCode는 서브도메인별로 나누지 않고 **서비스당 enum 하나**로 시작한다(예: `OrderErrorCode`, `PaymentErrorCode`). 서비스 간 경계는 이미 MSA로 분리되어 있어 충돌 문제가 없고, 서비스 하나 안에서까지 미리 쪼개는 건 지금 단계에서는 과함. 특정 서브도메인의 코드가 눈에 띄게 많아지면(대략 10개 이상, 또는 다른 담당자가 그 부분만 전담하게 되면) 그때 그 부분만 별도 enum으로 분리한다.

## 예외 던지기
- 비즈니스 규칙 위반은 `BusinessException(ErrorCode)`를 던진다 (application/domain 계층)
- 외부 연동(PG, AI, 스트리밍 등) 실패는 `DependencyFailureException` 계열로 감싸서 던진다 (infrastructure 계층). 기본값은 `CommonErrorCode.DEPENDENCY_FAILURE`(503)이며, 더 구체적으로 표현하고 싶으면 도메인 ErrorCode를 직접 넘긴다
- 컨트롤러에서 직접 `try-catch`로 응답을 조립하지 않는다 — `GlobalExceptionHandler`(`@RestControllerAdvice`)가 일괄 처리

## GlobalExceptionHandler 구성 (모듈 분리)
- 실제 처리 로직은 `modules:common-webmvc`의 `AbstractGlobalExceptionHandler`에 있다. **Servlet 기반(Spring MVC) 서비스만** 이 모듈에 의존한다.
- 각 서비스는 `presentation` 패키지에 아래처럼 2줄짜리 구현체만 둔다:
  ```java
  @RestControllerAdvice
  public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
  }
  ```
- `modules:common` 자체는 웹 프레임워크(Servlet/WebFlux)를 모른다 — `ErrorCode`/`CommonErrorCode`/`BusinessException`/`DependencyFailureException`/`ErrorResponse`만 있는 순수 Java 모듈이다.
- WebFlux 서비스가 생기면 그때 `modules:common-webflux`를 같은 패턴으로 추가한다(아직 없음). Servlet용 `AbstractGlobalExceptionHandler`(`ResponseEntityExceptionHandler` 상속)와 WebFlux용은 시그니처가 달라 공존할 수 없다.
- 서비스 `build.gradle`은 `modules:common`이 아니라 `modules:common-webmvc`에 의존한다 (`api` 관계로 `modules:common`도 같이 딸려온다 — 두 개 다 선언할 필요 없음).

## 로깅
- 예외 스택트레이스를 사용자 응답에 노출하지 않는다
- 로그에 비밀번호·토큰·개인정보를 남기지 않는다 (`security.md` S10 참고)