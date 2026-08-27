---
paths:
  - "**/presentation/controller/**"
  - "**/presentation/dto/**"
---

# API 컨벤션

## 공통 응답 포맷
- 성공 응답: 도메인 DTO를 HTTP 상태 코드와 함께 **래퍼 없이 직접 반환**
- 목록 조회(페이지네이션): `PageResponse<T>` 사용
  ```json
  { "content": [...], "page": 0, "size": 20, "totalElements": 134, "totalPages": 7, "hasNext": true }
  ```
- 에러 응답: `{ "code": "...", "message": "...", "detail": null }` — `ErrorResponse.of(ErrorCode)`로 생성

## URL / 버저닝
- URL: kebab-case, `/api/v1/` 프리픽스 고정
- 필드 **추가**는 버전을 올리지 않음 (클라이언트는 모르는 필드를 무시하도록 구현되어 있다고 가정)
- 필드 **삭제·이름 변경·타입 변경** 등 브레이킹 체인지만 버전 상향 (`/api/v2/`), 구버전은 최소 유지 기간 후 폐기

## 컨트롤러 작성 규칙
- 컨트롤러는 요청 검증(`@Valid`) + 애플리케이션 서비스 호출 + 응답 변환만 수행, 비즈니스 로직을 넣지 않는다
- 요청/응답 DTO는 `presentation/dto`에 위치, 도메인 엔티티를 그대로 반환하지 않는다
- 에러 코드 등록·예외 처리 관련 규칙은 `error-handling.md` 참고