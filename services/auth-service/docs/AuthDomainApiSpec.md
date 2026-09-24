## 인증(Auth) 도메인

> 실행 중인 서비스가 `GET /api/v1/auth/api-docs.yaml`로 OpenAPI 3 문서를 제공합니다.
> **구현 현황은 생성 스펙이 기준입니다** — 이 문서는 설계 의도와 비즈니스 규칙, 생성 스펙은 실제 구현만 담습니다.
> 파일로 뽑아 프론트에 전달하는 방법은 `docs/development-workflow-guide.md`의 "API 스펙(OpenAPI) 전달" 참고.

### 인증 도메인 엔드포인트 목록

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/identity-verifications` | X | 본인인증(PortOne 통합인증) 결과 조회 |
| GET | `/api/v1/auth/check-email` | X | 이메일 중복 확인 |
| POST | `/api/v1/auth/signup` | X | 계정 생성(일반가입) |
| POST | `/api/v1/auth/signup/social` | X | 계정 생성(소셜가입) |
| POST | `/api/v1/auth/login` | X | 일반 로그인 |
| POST | `/api/v1/auth/login/social` | X | 소셜 로그인 |
| POST | `/api/v1/auth/token/refresh` | X (Refresh Token 쿠키가 인증 수단) | Access Token 재발급 |
| POST | `/api/v1/auth/logout` | X (Refresh Token 쿠키로 대상 식별) | 로그아웃 — 이 기기의 refresh 토큰 폐기 + 쿠키 만료 |
| POST | `/api/v1/auth/find-email` | X | 이메일 찾기 1단계 — 마스킹 |
| POST | `/api/v1/auth/find-email/reveal` | X | 이메일 찾기 2단계 — 전문 공개 |
| POST | `/api/v1/auth/reset-password` | X | 비밀번호 재설정 링크 발송 |
| POST | `/api/v1/auth/reset-password/confirm` | X | 재설정 링크로 비밀번호 변경 |
| POST | `/api/v1/auth/reset-password/confirm` | X (재설정 토큰이 인증 수단) | 재설정 토큰으로 비밀번호 설정 |
| PATCH | `/api/v1/auth/password` | O | 비밀번호 변경(마이페이지, 로그인 상태) |
| GET | `/api/v1/auth/jwks` | X | 토큰 서명 검증용 공개키(JWKS) — 게이트웨이 전용 |

> 회원가입 관련 두 엔드포인트를 하나로 합치지 않은 이유는 회원 도메인 문서 참고. 로그인은 "아이디" 없이 이메일 단일 식별자로 통일한다(요구사항정의서 확인, `accounts.login_id` 컬럼 제거).

---

### 인증 공통 — Refresh Token은 httpOnly 쿠키

로그인/회원가입 성공 시 서버는 Access Token은 응답 바디로, **Refresh Token은 아래 속성의 쿠키로** 내려준다. 응답 바디에는 Refresh Token을 포함하지 않는다.

```
Set-Cookie: refreshToken=<value>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=1209600
```

- `HttpOnly`: JS에서 값 접근 불가 → XSS로 토큰 탈취 원천 차단.
- `SameSite=Strict`: 크로스사이트 요청에는 쿠키가 실리지 않아 CSRF 방어.
- `Path`를 재발급 엔드포인트로 좁혀서, 다른 API 요청에는 이 쿠키가 아예 실리지 않음(노출 범위 최소화).
- **로그인/회원가입 등 최초 발급 시점에도** 해당 토큰을 `refresh_tokens` 테이블에 INSERT한다 — 그래야 이후 `POST /api/v1/auth/token/refresh`의 회전·재사용 탐지 로직이 첫 재발급부터 정상 동작한다.

> **(선택적 개선, P2)** `SameSite=Strict`만으로도 기본적인 CSRF 방어는 충분하지만, 시간이 남으면 Double Submit Cookie 등 CSRF 토큰 방식을 추가로 검토할 수 있다. 지금 단계의 필수 요구사항은 아님.

---

### 본인인증(PortOne 통합인증) 결과 조회 (AUTH-005)

> 2026-08-31: 벤더가 PortOne 통합인증(KG이니시스, 카카오/네이버/PASS/토스/금융인증서 등)으로 확정되면서, 기존 "휴대폰 인증번호 발송"(AUTH-004) 엔드포인트는 폐기됐다 — 클라이언트가 PortOne JS SDK(`PortOne.requestIdentityVerification`)로 인증창을 직접 열어 인증을 완료하므로 서버가 인증번호를 발송·관리할 필요가 없다. 서버는 인증 완료 후 발급되는 `identityVerificationId`로 결과를 조회·검증하는 역할만 한다.

```
POST /api/v1/auth/identity-verifications
```

Auth Required: **X**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `identityVerificationId` | String | Y | PortOne SDK 인증 완료 후 발급된 본인인증 건 식별자 |

Response Body

```json
{
  "verificationToken": "018e1234-abcd-7xxx-xxxx-xxxxxxxxxxxx",
  "expiresAt": "2026-08-26T10:15:00"
}
```

Validation / Business Rules

- 서버는 PortOne 단건조회 API(`GET https://api.portone.io/identity-verifications/{id}?storeId={PG_STORE_ID}`, `Authorization: PortOne {PG_APIKEY}`)를 호출해 `status`를 확인한다. `storeId`를 명시해 접근 권한 있는 상점의 인증 건만 조회되도록 한다(다른 상점 소유 건 조회 방지).
- `status != VERIFIED` 시 401(`TOKEN_INVALID`).
- PortOne API 호출 실패(네트워크·5xx) 시 503(`DEPENDENCY_FAILURE`).
- 검증 성공 시 `verifiedCustomer`(name/phoneNumber/birthDate)를 Redis에 TTL 30분으로 저장하고 `verificationToken`(UUID)을 발급한다. CI/DI는 이번 슬라이스에서 저장하지 않는다.
- `verificationToken`은 이후 회원가입 요청에 함께 제출해야 하는 단기 토큰이며, 1회 소비(get-and-delete) 후 즉시 폐기된다.

---

### 이메일 중복 확인 (AUTH-006)

```
GET /api/v1/auth/check-email
```

Auth Required: **X**

Query Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | Y | 확인할 이메일 |

Response Body

```json
{
  "available": true
}
```

Validation / Business Rules

- 형식 오류(이메일 형식 위반) 시 400.
- `accounts.email` UNIQUE 인덱스 기준 존재 여부 조회.

---

### 계정 생성(일반가입) (AUTH-007)

```
POST /api/v1/auth/signup
```

Auth Required: **X**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `password` | String | Y | 비밀번호 |
| `email` | String | Y | 이메일 |
| `verificationToken` | String | Y | 휴대폰 인증 완료 토큰 |
| `name` | String | Y | 실명 (프로필용) |
| `nickname` | String | **Y** | 닉네임(표시명). 공개 화면에서 실명 대신 쓴다 — **실명으로 대신 채우지 않는다**(security.md S9) |
| `phoneNumber` | String | Y | 휴대전화번호 (프로필용) |
| `agreedTerms` | Array | Y | 약관 동의 목록 |
| `address` | Object | N | 선택 입력 주소 |

Response Body (Set-Cookie로 refreshToken 발급됨)

```json
{
  "accountId": "018e1234-abcd-7xxx-xxxx-xxxxxxxxxxxx",
  "memberId": "018e1234-abcd-7xxx-xxxx-xxxxxxxxxxxx",
  "accessToken": "..."
}
```

Validation / Business Rules

- 이메일 중복 시 409.
- 비밀번호는 솔트 포함 해시(BCrypt)로 저장, 복잡도 규칙 검증.
- `verificationToken` 검증 실패/만료/미존재 시 401(`TOKEN_INVALID`). 검증 시 Redis에서 1회 소비(get-and-delete)하고, 저장된 `phoneNumber`가 요청의 `phoneNumber`와 일치하는지 대조한다 — 불일치 시에도 동일하게 401.
- **1인 1계정(#150, 2026-09-24 PM 확정)**: 본인인증한 **이름+전화번호**로 이미 계정이 있으면 409(`ACCOUNT_ALREADY_EXISTS`, "이미 계정이 존재합니다.") — 이메일만 바꿔 재가입하는 것을 막는다. 본인인증 **뒤에** 검사한다(인증 없이 가입 여부를 캐낼 수 없게). 전화번호를 바꾼 뒤 재가입은 예외로 보고 막지 않는다. PortOne(KG이니시스 통합인증)은 DI를 주지 않아 DI 기준은 쓸 수 없다. DB UNIQUE는 기존 dev 중복 계정 때문에 걸지 않았다(운영 오픈 전 정리 후 검토).
- **계정 생성 및 프로필 생성 처리 순서(보상 트랜잭션)**:
    1. auth-service가 `accounts` 행을 생성하고 **커밋**한다(네트워크 호출 중 트랜잭션을 열어두지 않기 위함).
    2. 커밋 후 회원 도메인의 `POST /api/v1/members`를 동기 호출해 프로필을 생성한다.
    3. 이 호출이 실패하면, 분산 트랜잭션이 아니라 **보상 트랜잭션**으로 방금 커밋한 `accounts` 행을 삭제하고 클라이언트에는 503(`DEPENDENCY_FAILURE`)을 응답한다.
    4. 보상 삭제 자체가 실패하는 극단적 케이스에 대비해 `AUTH-012`(고아 계정 정리 배치, 하단 참고)가 안전망 역할을 한다.
- 필수 약관 미동의 시 400.

---

### 계정 생성(소셜가입) (AUTH-008)

```
POST /api/v1/auth/signup/social
```

Auth Required: **X**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `provider` | Enum | Y | `KAKAO` \| `GOOGLE` |
| `authorizationCode` | String | N | OAuth 인가 코드. `signupToken`을 제출하는 경우 생략 가능 |
| `signupToken` | String | N | 소셜 로그인 시도 중 미가입으로 판정되며 발급된 토큰(하단 로그인 API 참고). `authorizationCode` 대신 제출 가능 |
| `agreedTerms` | Array | Y | 약관 동의 목록 |
| `name` | String | **Y** | 이름. 본인인증 없이 사용자가 입력한 값(member 프로필 저장용) |
| `nickname` | String | **Y** | 표시명. 프론트가 제공자 닉네임(`login/social` 응답의 `name`)으로 폼을 미리 채우고 사용자가 고칠 수 있다. **서버는 제공자 값을 쓰지 않는다** — 요청값이 유일한 출처다(이메일과 반대) |
| `phoneNumber` | String | **Y** | 전화번호. 본인인증 없이 사용자가 입력한 값(member `phone_number` NOT NULL) |

Response Body (Set-Cookie로 refreshToken 발급됨)

```json
{
  "accountId": "018e5678-abcd-7xxx-xxxx-xxxxxxxxxxxx",
  "memberId": "018e5678-abcd-7xxx-xxxx-xxxxxxxxxxxx",
  "accessToken": "..."
}
```

Validation / Business Rules

> **본인인증 없음(#150, 2026-09-24 결정)**: 2026-09-10 구현 정정으로 받던 `verificationToken`을 **제거했다.**
> 본인인증은 일반가입(AUTH-007)에만 남는다. `name`·`phoneNumber`는 요청값을 그대로 member 프로필에 넘기고,
> **계정의 이름·전화번호 해시는 비워 둔다** — 인증 안 된 값이 이메일 찾기(AUTH-009)·비밀번호 재설정·일반가입
> 중복 판정에 섞이지 않게 하기 위해서다. 그래서 소셜 전용 계정은 이메일 찾기 대상이 아니고(소셜 로그인으로 진입),
> 소셜 계정이 있는 사람도 일반가입은 따로 할 수 있다(PM 결정: 중복 방지는 일반가입만).

- `authorizationCode`/`signupToken` 둘 다 없으면 400.
- `authorizationCode` 제출 시: 인가 코드로 제공자 사용자정보 조회, 응답값 검증 후 사용.
- `signupToken` 제출 시: 토큰에 담긴 검증된 소셜 신원 정보를 그대로 사용(재검증 불필요, OAuth 핸드셰이크 재수행 없음). 만료 시 401 → 소셜 로그인부터 재시도 안내.
- 기존 연동 계정(`social_provider`+`social_id`) 존재 시 409 → 로그인 유도.
- 계정 생성 후 회원 도메인 `POST /api/v1/members` 동기 호출 — 실패 시 보상 트랜잭션은 AUTH-007과 동일.
  (소셜 전용 엔드포인트를 두지 않는다: 기존 요청 본문에 비밀번호도 소셜 필드도 없어 provider와 무관하다.)

---

### 일반 로그인 (AUTH-001)

```
POST /api/v1/auth/login
```

Auth Required: **X**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | Y | 이메일 |
| `password` | String | Y | 비밀번호 |

Response Body (Set-Cookie로 refreshToken 발급됨)

```json
{
  "accessToken": "...",
  "mustChangePassword": false,
  "member": {
    "memberId": "018e1234-abcd-7xxx-xxxx-xxxxxxxxxxxx",
    "nickname": "응원왕"
  }
}
```

Validation / Business Rules

- 서버에서 비밀번호 해시 비교(평문 비교 금지).
- 이메일·비밀번호 불일치 시 401 — 어느 쪽이 틀렸는지 구분해 노출하지 않고 통일된 메시지로 응답.
- **5회 연속 실패 시 계정 30분 자동 잠금**(`locked_until = now() + 30분`). 잠금 중 로그인 시도는 423(`AuthErrorCode.ACCOUNT_LOCKED`) + 잠금 해제 예정 시각(`detail.lockedUntil`) 안내. 이메일/SMS를 통한 셀프 해제는 P2로 보류(MVP 범위 아님).
- `accounts.must_change_password = true`인 계정으로 로그인 성공 시 응답의 `mustChangePassword`를 true로 반환 — 프론트는 이 값을 보고 비밀번호 변경 화면으로 즉시 리다이렉션.
- 응답에 비밀번호·해시 절대 미포함.

---

### 이메일 충돌 정책 (PM 확정, 2026-09-10)

같은 이메일이 이미 쓰이고 있을 때의 처리. **판정은 `EmailConflictChecker` 한 곳에서만 한다** —
제공자가 이메일을 주면 소셜 로그인 시점에, 주지 않으면(카카오는 이메일 동의가 선택) 사용자가
이메일을 입력하는 가입 시점에 판정하게 되어 진입점이 둘이기 때문이다.

| 상황 | 처리 |
| --- | --- |
| **A.** 자체가입 계정과 같은 이메일로 소셜 로그인 | 200 `needsLink: true` + `linkToken` → 본인인증 → `POST /auth/social/link`로 연동 |
| **B.** 소셜 계정과 같은 이메일로 일반 회원가입 | 409 `SOCIAL_ACCOUNT_EXISTS`, `detail.provider`로 어느 제공자인지 전달 |
| **C.** 소셜 계정과 같은 이메일로 다른 소셜 로그인 | 위와 동일 |

`uq_accounts_email`이 UNIQUE라 "이메일당 계정 하나"라는 이 정책과 DB 제약이 일치한다.

```json
{ "code": "SOCIAL_ACCOUNT_EXISTS", "message": "이미 KAKAO 소셜 로그인 계정이 존재합니다.",
  "detail": { "provider": "KAKAO" } }
```

---

### 소셜 계정 연동 (정책 A)

```
POST /api/v1/auth/social/link
```

Auth Required: **X** (`linkToken` + 본인인증 토큰이 인증 수단)

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `linkToken` | String | Y | 소셜 로그인이 정책 A로 판정하며 발급한 토큰 |
| `verificationToken` | String | Y | 본인인증(`POST /auth/identity-verifications`) 결과 토큰 |

Validation / Business Rules

- **연동 대상 계정은 `linkToken`에서만 나온다** — 클라이언트가 `accountId`를 보내지 않는다. 보내게 하면 본인인증만 통과하면 아무 계정이나 지목할 수 있다.
- 본인인증으로 확인된 휴대폰번호가 그 계정 주인의 것인지 member-service(`POST /members/{accountId}/phone-verification`)에 확인한다. auth-service는 휴대폰번호를 보관하지 않는다.
- 불일치·계정 없음이면 403. 둘을 구분해 알려주지 않는다.
- 두 토큰 모두 1회 소비, 만료 시 401.
- 연동 후에도 기존 비밀번호는 유지된다 — 일반 로그인과 소셜 로그인이 둘 다 가능하다.
- 소셜 로그인 응답에는 **이메일을 담지 않는다** — 소셜 로그인 시도만으로 타인의 가입 이메일이 드러나면 안 된다.

---

### 소셜 로그인 (AUTH-002)

```
POST /api/v1/auth/login/social
```

Auth Required: **X**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `provider` | Enum | Y | `KAKAO` \| `GOOGLE` |
| `authorizationCode` | String | Y | OAuth 인가 코드 |

Response Body — 연동 계정 있음 (Set-Cookie로 refreshToken 발급됨)

```json
{
  "needsSignup": false,
  "accessToken": "...",
  "mustChangePassword": false,
  "member": {
    "memberId": "018e5678-abcd-7xxx-xxxx-xxxxxxxxxxxx",
    "nickname": "응원왕"
  }
}
```

Response Body — 연동 계정 없음 (회원가입 필요)

```json
{
  "needsSignup": true,
  "provider": "KAKAO",
  "signupToken": "018e9999-abcd-7xxx-xxxx-xxxxxxxxxxxx"
}
```

Validation / Business Rules

- 인가 코드로 제공자 사용자정보 조회, 응답값 검증 후 사용.
- 연동된 계정이 없으면 **404로 실패 처리하지 않고** `needsSignup: true` + `signupToken`을 200으로 응답 — 프론트가 별도 리다이렉트 없이 바로 회원가입 플로우로 전환할 수 있도록 함(OAuth 인가 코드는 보통 1회용이라, 이 시점엔 이미 소진되어 재사용이 안 되므로 자체 토큰으로 신원 정보를 다시 전달).
- `signupToken`은 짧은 만료시간(예: 10분)을 가지며, `POST /api/v1/auth/signup/social`에서 소비된다.
- 제공자 응답 실패 시 503.

---

### Access Token 재발급 (AUTH-003)

```
POST /api/v1/auth/token/refresh
```

Auth Required: **X** (Refresh Token 쿠키가 인증 수단)

Request Body: 없음 (`refreshToken` 쿠키가 브라우저에 의해 자동 첨부됨)

Response Body (Set-Cookie로 **새** refreshToken 발급됨)

```json
{
  "accessToken": "..."
}
```

Validation / Business Rules

- 쿠키에 `refreshToken`이 없으면 401.
- **회전(Rotation) + 재사용 탐지를 하나의 원자적 쿼리로 처리**:
  ```sql
  DELETE FROM refresh_tokens
  WHERE token_id = :tokenId AND expires_at > now()
  RETURNING account_id;
  ```
    - 행이 반환되면(정상): 그 `account_id`로 새 토큰을 발급해 `refresh_tokens`에 INSERT, 새 Access Token과 함께 Set-Cookie로 응답.
    - 행이 반환되지 않으면(이미 삭제된 토큰이 다시 제출됨 = 탈취 의심): 401 응답과 함께 `DELETE FROM refresh_tokens WHERE account_id = :accountId`로 해당 계정의 모든 세션을 강제 로그아웃시킨다(이 경우 토큰 자체가 만료·위조되어 `account_id`를 모를 수 있으므로, JWT 서명은 유효하지만 DB에 없는 경우에 한해 토큰 안의 `account_id` 클레임을 사용— 서명 자체가 무효면 그냥 401만 반환).
- `DELETE ... RETURNING`은 PostgreSQL에서 원자적으로 처리되므로 별도 트랜잭션/락 없이도 동시 재발급 요청 간 레이스 컨디션이 없다.
- 위변조된 토큰(서명 불일치 등)은 별도로 401(`TOKEN_INVALID`) 처리.

> Redis 대신 `refresh_tokens` 테이블(RDBMS)로 시작한다. 트래픽이 늘어 이 조회가 결제/주문 등 비즈니스 쿼리와 자원을 다투는 지점이 실제로 관찰되면 그때 Redis 이전을 검토하기로 함 — 지금 미리 옮길 이유를 만들지 않고, 실제로 필요해지는 순간을 지켜보기로 함(2026.08.26).

---

### 이메일 찾기 (AUTH-009) — 2단계

> ⚠️ **초안에서 흐름이 바뀌었다.** 초안은 "본인인증 먼저 → 마스킹 이메일을 SMS로만 전달"이었으나,
> 화면(피그마)이 **인증 전에 마스킹 이메일을 보여주고 인증 후 전문을 공개**하는 흐름이라 그에 맞췄다.

#### 1단계 — 마스킹 조회 (본인인증 전)

```
POST /api/v1/auth/find-email
```

Auth Required: **X**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | String | Y | 이름 |
| `phoneNumber` | String | Y | 휴대전화번호 |

Response Body

```json
{ "maskedEmail": "1234q***@gmail.com" }
```

#### 2단계 — 전문 공개 (본인인증 후)

```
POST /api/v1/auth/find-email/reveal
```

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `verificationToken` | String | Y | `POST /identity-verifications`가 발급한 본인인증 완료 토큰 |

```json
{ "email": "1234qwer@gmail.com" }
```

Validation / Business Rules

- **마스킹 규칙**: 로컬파트 **뒤 3자를 `***`**, 도메인은 그대로. `1234qwer@gmail.com` → `1234q***@gmail.com`. 로컬파트가 4자 미만이면 통째로 가린다(`ab@x.com` → `***@x.com`). *짧은 주소 규칙은 미확정 — 현재 동작이 잠정 기본값이다.*
- 1단계는 가입 계정이 없어도 `200`이고 `maskedEmail`만 `null`이다 — 응답 **형태**는 계정 유무와 무관하게 같다.
- ⚠️ **1단계는 계정 존재 여부를 드러낸다.** 마스킹 이메일을 화면에 보여주는 게 요구사항이라 피할 수 없고, `CLAUDE.md`의 anti-enumeration 원칙과 충돌하는 유일한 지점이다. 대신 전화번호 기준 **10분 5회** 시도 제한을 둔다(초과 시 `429`). 제한 카운터 키에는 번호 원문이 아니라 블라인드 인덱스 해시를 쓴다.
- **2단계는 이름·전화번호를 본문으로 받지 않는다.** 본인인증 토큰에 실린 값으로만 조회한다 — 본문으로 받으면 1단계에서 알아낸 남의 번호에 자기 인증 토큰을 붙여 전문을 꺼낼 수 있다.
- `verificationToken`은 1회용이다. 검증 실패·재사용 모두 `401`.
- 본인인증을 통과했는데 가입 계정이 없으면 `404`다 — 그 번호의 소유자임이 확인된 사람에게 답하는 것이라 열거가 아니다.
- 조회는 `accounts.phone_hash`·`name_hash`(HMAC-SHA256)로 한다. auth가 전화번호 원문을 보관하지 않고도 찾을 수 있게 한 것이며, member-service에 "번호 → 계정"을 묻지 않는다(그 방향은 member가 명시적으로 거부한다).

---

### 비밀번호 재설정 링크 발송 (AUTH-010)

```
POST /api/v1/auth/reset-password
```

Auth Required: **X**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | String | Y | 이름 |
| `phoneNumber` | String | Y | 휴대전화번호 |
| `email` | String | Y | 이메일 |

Response Body

```json
{
  "message": "입력하신 정보와 일치하는 계정이 있다면 재설정 링크를 보내드립니다."
}
```

그리고 링크로 새 비밀번호를 설정한다.

```
POST /api/v1/auth/reset-password/confirm
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `token` | String | Y | 메일 링크의 토큰(UUID) |
| `newPassword` | String | Y | 새 비밀번호(가입·변경과 같은 복잡도 규칙) |

Validation / Business Rules

- **세 값이 모두 일치할 때만 발송한다.** 초안은 이메일만 받았는데(아래 [가정] 참고), 세 값을 받는 쪽이 그 가정을 부분적으로 메운다.
- 계정 존재 여부와 무관하게 항상 동일한 응답을 반환. 이메일 찾기 1단계와 달리 **돌려주는 내용이 없어** 완전히 같은 응답을 유지할 수 있다.
- 재설정 토큰은 단기 만료(30분)·1회용. `password_reset_tokens` 테이블을 쓰며 `refresh_tokens`와 동일 패턴이다.
- **확인과 폐기가 `DELETE ... RETURNING` 한 문장**이다 — 나누면 같은 링크를 동시에 두 번 눌렀을 때 둘 다 통과한다. 두 번째 사용은 `401`, 형식이 깨진 토큰도 같은 `401`이다(형식 오류를 400으로 구분하면 토큰 생김새를 알려주는 셈이다).
- **재발급 시 이전 토큰을 전부 지운다** — 메일함에 쌓인 옛 링크가 계속 살아있으면 안 된다.
- **비밀번호를 바꾸면 그 계정의 refresh token을 전부 지운다** — 바꾸는 이유가 탈취 의심인데 기존 세션이 살아 있으면 바꾼 의미가 없다.
- [가정] 여전히 SMS 본인인증 단계는 거치지 않는다 — "이메일 링크 클릭 = 본인"으로 간주하되, 이름·전화번호 일치를 추가 조건으로 둔다.
- 🔴 **메일 발송 인프라가 아직 없다.** `MailSender` 포트와 `LoggingMailSender` 스텁만 있고 실제 발송은 안 된다. SES/SMTP가 정해지면 구현체만 추가한다. 운영 프로필은 `mail.mode`에 기본값이 없어 **미설정이면 기동이 실패**한다.
- 이메일 발송 실패 시 503.

---

### 재설정 토큰으로 비밀번호 설정 (AUTH-014, 신규)

```
POST /api/v1/auth/reset-password/confirm
```

Auth Required: **X** (재설정 토큰이 인증 수단)

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `resetToken` | String | Y | 이메일로 받은 재설정 링크의 토큰 |
| `newPassword` | String | Y | 새 비밀번호 |

Response Body

```json
{ "message": "비밀번호가 변경되었습니다." }
```

Validation / Business Rules

- 토큰 존재·미만료·미사용 확인 후 새 비밀번호 해시 저장.
- 토큰은 사용 즉시 폐기(1회용) — 같은 토큰으로 재요청 시 401.
- 토큰 없음/만료/이미 사용됨 → 401 → 재발급(AUTH-010) 요청 유도.
- 이 흐름으로 설정된 비밀번호는 이미 사용자가 직접 새로 정한 것이므로 `must_change_password`를 별도로 세팅하지 않음(임시 비밀번호 강제 변경 흐름과 다름).

---

### 비밀번호 변경 (신규)

```
PATCH /api/v1/auth/password
```

Auth Required: **O**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `currentPassword` | String | Y | 현재(또는 임시) 비밀번호 |
| `newPassword` | String | Y | 새 비밀번호 |

Response Body

```json
{ "message": "비밀번호가 변경되었습니다." }
```

Validation / Business Rules

- `currentPassword` 불일치 시 401.
- 새 비밀번호 복잡도 규칙 검증(회원가입과 동일 기준).
- 변경 성공 시 `accounts.must_change_password = false`로 갱신.
- 로그인 상태(Access Token)를 전제로 하므로, 임시 비밀번호로 먼저 `POST /api/v1/auth/login`을 통과한 뒤 호출하는 흐름.

---

### 공개키 조회 (JWKS, 신규)

`GET /api/v1/auth/jwks`

Auth Required: **X** — 담기는 건 **공개키뿐**이라 인증을 걸지 않는다. 이 값으로 할 수 있는 건 서명 검증이고, 토큰 발급(서명)에는 개인키가 필요한데 그건 auth-service 밖으로 나가지 않는다.

호출 주체는 사실상 게이트웨이 하나다. 게이트웨이가 기동/키 갱신 시 이 엔드포인트에서 공개키를 가져와 캐싱하고, 이후 들어오는 Access Token의 RS256 서명을 검증한다.

**응답 200**

```json
{
  "keys": [
    {
      "kty": "RSA",
      "n": "<modulus, base64url>",
      "e": "AQAB",
      "kid": "<공개키 지문(RFC 7638)>"
    }
  ]
}
```

- `kid`는 발급 토큰의 JWS 헤더 `kid`와 일치한다. 키를 교체하면 지문이 바뀌므로 `kid`도 자동으로 따라간다(로테이션 시 설정 변경 불필요).
- 개인 파라미터(`d`/`p`/`q` 등)는 포함되지 않는다.

> **경로가 표준 `/oauth2/jwks`나 `/.well-known/jwks.json`이 아닌 이유**: `api-convention.md`가 `/api/v1/` 프리픽스를 고정으로 두고 있고, 이 JWKS는 외부 표준 클라이언트가 아니라 우리 게이트웨이만 읽는다(URI를 게이트웨이 설정으로 직접 주므로 표준 경로일 이점이 없다).

---

### 고아 계정 정리 배치 (AUTH-012, 신규 — 안전망)

```
(배치 작업, 엔드포인트 없음)
```

Auth Required: 해당 없음

Validation / Business Rules

- 주기적으로(예: 1시간마다) `accounts` 테이블에서 생성된 지 일정 시간(예: 10분)이 지났는데도 회원 도메인에 대응하는 `members` 프로필이 없는 행을 조회한다.
- 대상 발견 시 계정을 삭제하거나(가장 단순), 운영 알림만 발송하고 수동 확인을 거치게 한다(더 안전하지만 운영 부담 있음) — 방식은 팀 결정 필요.
- AUTH-007/008의 보상 트랜잭션이 정상 동작하면 이 배치가 처리할 대상은 거의 없어야 하며, 이 배치는 어디까지나 이중 실패에 대한 안전망이다.

> **비고**: 처리 방식(자동 삭제 vs 운영 알림 후 수동 확인)은 **의도적으로 결정을 보류**한다(2026.08.26). 보상 트랜잭션이 정상 동작하는 한 이 배치가 실제로 처리할 대상은 극히 드물 것으로 예상되므로, 지금 정교하게 설계하기보다 실제로 고아 계정이 발생하는 사례를 관찰한 뒤 그 패턴에 맞춰 방식을 정하기로 함. 우선순위를 P2로 낮춤.

---

### 만료된 Refresh Token 정리 배치 (AUTH-013, 신규)

```
(배치 작업, 엔드포인트 없음)
```

Auth Required: 해당 없음

Validation / Business Rules

- `refresh_tokens`는 재발급 시 `DELETE ... RETURNING`으로 지워지지만, **끝까지 재발급 없이 그냥 만료된 토큰**(사용자가 로그아웃 안 하고 브라우저만 닫은 경우 등)은 테이블에 죽은 행으로 남는다.
- 주기적으로(예: 1일 1회) `expires_at < now()`인 행을 DELETE.
- AUTH-012와 마찬가지로 우선순위는 낮음(P2) — 트래픽이 적은 지금 단계에서는 죽은 행이 성능에 영향을 줄 정도로 쌓이지 않는다.

---

## 반영 이력

- **[확정] Refresh Token → httpOnly Secure SameSite=Strict 쿠키**: 응답 바디에서 제거, `POST /api/v1/auth/token/refresh` 요청 바디도 제거(쿠키 자동 첨부).
- **[확정, 재조정] Refresh Token 회전 + 재사용 탐지**: 저장소를 Redis에서 **PostgreSQL `refresh_tokens` 테이블**로 변경(2026.08.26) — `DELETE ... RETURNING`으로 원자적 확인+폐기 처리. 트래픽이 늘어 실제로 옮길 이유가 생기면 그때 Redis 이전 검토. 새 인프라/의존성 추가 없음.
- **[확정] 계정 생성 보상 트랜잭션**: "롤백"이 아니라 커밋 후 실패 시 별도 DELETE로 보상. 안전망으로 `AUTH-012` 배치 신규 추가.
- **[확정, 의도적 보류] 고아 계정 정리 배치 처리 방식**: 자동 삭제 vs 수동 확인 중 지금 결정하지 않고, 실제 발생 사례를 관찰한 뒤 정하기로 함. 우선순위 P1 → P2.
- **[신규] 만료된 Refresh Token 정리 배치(AUTH-013)**: `refresh_tokens` 테이블 도입에 따라 추가된 저우선순위(P2) 청소 배치.
- **[확정] 소셜 로그인 미가입 처리**: `needsSignup` + `signupToken` 패턴. `POST /api/v1/auth/signup/social`에 `signupToken` 필드 추가.
- **[확정] 계정 잠금**: 5회 실패 시 30분 고정 자동 잠금. 셀프 해제(이메일/SMS)는 P2로 보류.
- **[확정] 비밀번호 변경 강제**: `accounts.must_change_password` 컬럼 신규 추가(SQL 반영 완료), 로그인 응답에 `mustChangePassword` 플래그 포함, `PATCH /api/v1/auth/password` 신규 엔드포인트 추가.

- **[확정] JWT 서명 RS256 전환 + JWKS 엔드포인트 신설**(2026.09.08): 게이트웨이 도입 초기에는 HS256 대칭키를 게이트웨이와 공유했으나(게이트웨이가 유출되면 토큰 위조까지 가능), 게이트웨이를 어디에도 배포하기 전에 RS256으로 전환. 개인키는 auth-service만 갖고 게이트웨이는 `GET /api/v1/auth/jwks`로 공개키만 가져가 검증한다. 공개키는 개인키에서 유도하므로 운영이 관리할 시크릿은 `JWT_PRIVATE_KEY` 하나다. **클라이언트 계약은 바뀌지 않았다** — 토큰 클레임(`sub`/`role`/`typ`/`iat`/`exp`)은 그대로고 `alg`와 `kid` 헤더만 달라져 프론트가 고칠 건 없다(단, 전환 시점에 기존 발급 토큰은 전부 무효화되어 재로그인이 필요하다).

## ⚠️ 남은 확인 필요 사항

(현재 없음)

---

### 로그아웃

```
POST /api/v1/auth/logout
```

Auth Required: **X** — access 토큰이 만료된 뒤에도 로그아웃할 수 있어야 한다. 대상은 Refresh Token 쿠키로 식별한다.

Response Body (200)

```json
{ "message": "로그아웃되었습니다." }
```

```
Set-Cookie: refreshToken=; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=0
```

- 쿠키의 refresh 토큰 **하나만** 폐기한다(다른 기기 세션은 유지).
- **멱등**: 쿠키가 없거나 무효·만료·이미 폐기된 토큰이어도 200이다. 이미 폐기된 토큰이어도 재사용 탐지(전체 세션 강제 로그아웃)를 트리거하지 않는다.
- 쿠키 Path를 `/api/v1/auth`로 둔 이유: 재발급(`/token/refresh`)과 로그아웃(`/logout`) 모두 이 쿠키를 받아야 한다. 재발급 경로로만 좁히면 브라우저가 로그아웃 요청에 쿠키를 싣지 않는다.
