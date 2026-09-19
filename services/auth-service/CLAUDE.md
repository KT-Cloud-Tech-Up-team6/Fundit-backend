# auth-service

> 루트 `CLAUDE.md`(레포 공통 규칙)와 `.claude/rules/`를 전제로, 여기는 auth-service에만 해당하는 내용만 다룹니다.

## 이 서비스가 하는 일
로그인, 토큰(Access/Refresh) 발급·검증·회전, 계정(자격증명) 소유. (`PRD.md` 2. 도메인/서비스 개요 기준)

본인인증(휴대폰/통합인증)은 이 서비스 자체 책임입니다. 회원 프로필·구매자/판매자 모드만 member-service 소관입니다 — 이 서비스는 회원가입 시 member-service를 동기 호출만 하고, 프로필 데이터를 직접 보관하지 않습니다.

## 먼저 읽을 문서
로그인/회원가입/토큰 관련 작업을 시작하기 전에 **`services/auth-service/docs/auth-functional-spec.md`를 먼저 읽으세요** — AUTH-001~014 기능별 요구사항, 예외 처리, 에러 코드 매핑표가 있습니다. 이 CLAUDE.md는 그 문서의 핵심만 요약한 것이지 대체하지 않습니다.

> 확인 상태: 본인인증 담당 서비스·AUTH-001 조회 대상·`V12`/`V13` 보안 코드·role 값 표기·AUTH-009 구현 방식 모두 확인 완료. 남은 건 PRD.md 본인인증 경계 수정(사용자가 별도 진행)과 스키마의 `role` 값 정정(사용자가 별도 진행)뿐입니다.

## 도메인 테이블 (스키마 확정됨)
- `accounts` — `id`(UUID, 애플리케이션에서 생성해 그대로 INSERT — member-service 호출 시 동일 ID 전달 목적), 이메일, 비밀번호 해시(소셜 전용 계정은 NULL), 소셜 제공자/소셜ID, 역할, `failed_login_count`, `locked_until`, `must_change_password`. **휴대폰번호·이름 원문은 없음** — 본인인증 값은 가입 시 member-service로 흘려보낼 뿐 auth가 보관하지 않는다. 다만 이메일 찾기(AUTH-009) 조회용으로 `phone_hash`·`name_hash`(HMAC-SHA256)만 둔다 — 되돌릴 수 없으므로 보관이 아니라 색인이다.
- **`email`에는 암호문이 들어간다**(AES-GCM, `security.md` S9). IV가 매번 달라 같은 평문도 매번 다른 암호문이라 `WHERE email = ?`가 성립하지 않는다 — 조회는 `email_hash`(블라인드 인덱스)로 하고, `UNIQUE` 제약도 평문이 아니라 이 컬럼에 걸려 있다. 변환은 `AccountMapper`(암/복호화)와 `AccountPersistenceAdapter`(해시)가 맡고 **application 계층은 평문만 본다**.
- `refresh_tokens` — Refresh Token 회전에 사용. Redis가 아니라 RDBMS로 시작(트래픽 늘어서 실제로 경합이 보이면 그때 이전 검토, 미리 옮기지 않음).
- `password_reset_tokens` — `refresh_tokens`와 동일 패턴(AUTH-010/014).

> **role 값**: `member`/`admin`(소문자)로 확정. 기존 DDL 초안의 `USER`/`ADMIN`은 사용자가 직접 스키마 쪽을 고쳐 맞추기로 함 — 이 코드베이스 어디서든 `member`/`admin` 기준으로 작성할 것.
>
> **AUTH-009(이메일 찾기) 구현 방식**: 초안은 "member에 휴대폰번호로 계정 ID를 묻는다"였으나 **auth 단독 조회로 변경**했다. member-service가 그 방향을 명시적으로 거부하고 있기 때문이다 — `MemberController.verifyPhone` javadoc: *"번호로 계정을 찾아주는 게 아니라 이미 아는 계정에 대해 맞는지만 답한다. 반대 방향(번호 → 계정)이었다면 번호만 넣어보며 가입 여부를 캐낼 수 있다."*
>
> 대신 가입 시점에 `SignupService`가 이미 쥐고 있는 `verifiedIdentity`의 이름·전화번호로 `phone_hash`·`name_hash`를 만들어 `accounts`에 넣고, 찾기는 그 해시로 조회한다. 내부 API·게이트웨이 차단 등록·네트워크 홉이 전부 없어지고 member의 설계 결정도 그대로 둔다.

## 핵심 설계 결정 (구현 시 반드시 지킬 것)
- **토큰 전달 방식**: Access Token은 응답 바디, Refresh Token은 httpOnly 쿠키(Secure, SameSite=Strict, Path를 재발급 엔드포인트로 제한).
- **Refresh Token 회전 + 재사용 탐지**: 재발급 시 기존 토큰을 `DELETE...RETURNING`으로 확인·폐기하고 신규 토큰을 발급. `RETURNING` 결과가 없으면(이미 폐기된 토큰 재사용 시도) 해당 계정의 **전체 세션을 강제 로그아웃**시킨다 — 단순히 401만 응답하고 끝내지 않는다.
- **회원가입 보상 트랜잭션**: 계정 생성 → member-service 동기 호출 → 실패 시 auth-service가 방금 만든 계정을 삭제. 이 트랜잭션 자체가 실패해 프로필 없는 계정이 남으면 AUTH-012 배치(고아 계정 정리)가 안전망 — 별도 2PC/사가 패턴을 새로 만들지 않는다.
- **계정 존재 여부 비노출(anti-enumeration)**: 이메일 찾기(AUTH-009), 비밀번호 재설정 링크 발송(AUTH-010)은 계정이 있든 없든 **항상 같은 성공 형태**로 응답한다. "계정 없음"을 404 등으로 구분해서 응답하면 계정 존재 여부가 노출되므로 절대 하지 않는다.

## 에러 코드
도메인 전용 코드는 `AuthErrorCode implements ErrorCode`로 만든다(서비스당 flat enum 1개 — `error-handling.md` 컨벤션). 정확히 어떤 코드가 필요한지는 `auth-functional-spec.md`의 "에러 코드 매핑" 표가 기준이다 — 새로 만들기 전에 그 표부터 확인할 것 (예: `INVALID_CREDENTIALS`, `ACCOUNT_LOCKED`, `EMAIL_ALREADY_EXISTS`). `TOKEN_INVALID`/`TOKEN_EXPIRED`/`INVALID_INPUT`/`DEPENDENCY_FAILURE`/`TOO_MANY_REQUESTS`는 이미 `CommonErrorCode`에 있으니 재정의하지 않는다.

## 이 서비스에서 절대 하지 말아야 할 것
- 비밀번호/자격증명 조회를 member-service로 위임하지 말 것 — auth-service 자체 `accounts` 테이블만 사용
- 이메일 찾기·비밀번호 재설정 응답에서 계정 존재 여부가 구분되게 하지 말 것 (위 anti-enumeration 참고)
- SMS 인증번호, 비밀번호 재설정 토큰, JWT 원문을 로그에 남기지 말 것 (`security.md` S9·S10)
- Refresh Token 재사용 탐지를 단순 401 응답으로만 처리하지 말 것 — 전체 세션 무효화까지 해야 함