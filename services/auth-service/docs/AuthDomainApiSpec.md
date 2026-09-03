## 인증(Auth) 도메인

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
| POST | `/api/v1/auth/find-email` | X | 이메일 찾기 |
| POST | `/api/v1/auth/reset-password` | X | 비밀번호 재설정 링크 발송 |
| POST | `/api/v1/auth/reset-password/confirm` | X (재설정 토큰이 인증 수단) | 재설정 토큰으로 비밀번호 설정 |
| PATCH | `/api/v1/auth/password` | O | 비밀번호 변경(마이페이지, 로그인 상태) |

> 회원가입 관련 두 엔드포인트를 하나로 합치지 않은 이유는 회원 도메인 문서 참고. 로그인은 "아이디" 없이 이메일 단일 식별자로 통일한다(요구사항정의서 확인, `accounts.login_id` 컬럼 제거).

---

### 인증 공통 — Refresh Token은 httpOnly 쿠키

로그인/회원가입 성공 시 서버는 Access Token은 응답 바디로, **Refresh Token은 아래 속성의 쿠키로** 내려준다. 응답 바디에는 Refresh Token을 포함하지 않는다.

```
Set-Cookie: refreshToken=<value>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/token/refresh; Max-Age=1209600
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
| `name` | String | N | 제공자 응답에 이름이 없을 경우 추가 입력 |
| `phoneNumber` | String | N | 제공자 응답에 없을 경우 추가 입력 |

Response Body (Set-Cookie로 refreshToken 발급됨)

```json
{
  "accountId": "018e5678-abcd-7xxx-xxxx-xxxxxxxxxxxx",
  "memberId": "018e5678-abcd-7xxx-xxxx-xxxxxxxxxxxx",
  "accessToken": "..."
}
```

Validation / Business Rules

- `authorizationCode`/`signupToken` 둘 다 없으면 400.
- `authorizationCode` 제출 시: 인가 코드로 제공자 사용자정보 조회, 응답값 검증 후 사용.
- `signupToken` 제출 시: 토큰에 담긴 검증된 소셜 신원 정보를 그대로 사용(재검증 불필요, OAuth 핸드셰이크 재수행 없음). 만료 시 401 → 소셜 로그인부터 재시도 안내.
- 기존 연동 계정(`social_provider`+`social_id`) 존재 시 409 → 로그인 유도.
- 계정 생성 후 회원 도메인 `POST /api/v1/members/social` 동기 호출 — 실패 시 보상 트랜잭션은 AUTH-007과 동일.

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

### 이메일 찾기 (AUTH-009)

```
POST /api/v1/auth/find-email
```

Auth Required: **X**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `phoneNumber` | String | Y | 휴대전화번호 |
| `verificationToken` | String | Y | 휴대폰 인증 완료 토큰 |

Response Body

```json
{
  "message": "가입된 계정이 있다면 SMS로 안내해드립니다."
}
```

Validation / Business Rules

- 계정 존재 여부와 무관하게 항상 동일한 응답을 반환(계정 존재 여부 노출 방지).
- 실제 마스킹된 이메일은 SMS로만 전달, API 응답 바디에는 포함하지 않음.
- `verificationToken` 검증 실패 시 401.

---

### 비밀번호 재설정 링크 발송 (AUTH-010)

```
POST /api/v1/auth/reset-password
```

Auth Required: **X**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `email` | String | Y | 이메일 |

Response Body

```json
{
  "message": "가입된 이메일이 있다면 재설정 링크를 보내드립니다."
}
```

Validation / Business Rules

- 계정 존재 여부와 무관하게 항상 동일한 응답을 반환(계정 존재 여부 노출 방지).
- 이메일로 계정 조회 후 재설정 토큰 생성(단기 만료·1회용, `password_reset_tokens` 테이블 — `refresh_tokens`와 동일 패턴).
- 재설정 링크(프론트 URL + 토큰)를 이메일로 발송.
- [가정] SMS 본인인증 단계 없이 이메일 소유 확인만으로 진행 — "이메일 링크 클릭 = 본인"으로 간주. 이메일 찾기·회원가입과 달리 휴대폰 인증을 거치지 않는 이 지점만 보안 수준이 다르다는 점을 팀에서 인지하고 있을 것.
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

## ⚠️ 남은 확인 필요 사항

(현재 없음)