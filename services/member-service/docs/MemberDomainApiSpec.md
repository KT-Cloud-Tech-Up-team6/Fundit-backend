## 회원(Member) 도메인

> 이 문서는 **MVP 구현 범위만** 다룹니다. MVP 범위 밖(후순위) 엔드포인트의 전체 스펙은 `MvpImplementationSummary.md`로 이동했습니다 — 여기엔 존재하지 않습니다.
> 2026-09-03 기준: 스키마 정리(is_foreigner/di_hash/phone_verified_at/business_type/business_info/seller_verified_at/current_mode 제외) 반영.

### 회원 도메인 엔드포인트 목록

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/terms` | X | 약관 목록 조회 |
| POST | `/api/v1/members` | X (내부 전용 — auth-service만 호출) | 회원 프로필 생성(일반가입) |
| GET | `/api/v1/members/me` | O | 내 프로필 조회 |
| PATCH | `/api/v1/members/me/mode` | O | 구매자/판매자 모드 전환 |
| PUT | `/api/v1/wishes/{projectId}` | O | 찜 등록(idempotent) |
| DELETE | `/api/v1/wishes/{projectId}` | O | 찜 해제(idempotent) |
| GET | `/api/v1/wishes` | O | 내 찜 목록 조회 |
| GET | `/api/v1/addresses` | O | 배송지 목록 조회 |
| POST | `/api/v1/addresses` | O | 배송지 등록 |

---

### 약관 목록 조회 (MEMBER-001)

```
GET /api/v1/terms
```

Auth Required: **X**

Request Body: 없음

Response Body

```json
[
  {
    "code": "SERVICE_USE",
    "title": "서비스 이용약관",
    "content": "...",
    "required": true,
    "version": "1.0"
  }
]
```

Validation / Business Rules

- 인증 불필요, 비로그인 상태에서도 조회 가능.
- 약관 데이터 자체가 없는 경우(운영 데이터 누락) 500 처리.

---

### 회원 프로필 생성(일반가입) (MEMBER-002)

```
POST /api/v1/members
```

Auth Required: **X** — **내부 전용 엔드포인트.** 게이트웨이 라우팅에서 제외해 외부 접근을 차단하고, `X-Internal-Api-Key` 헤더로 호출 주체를 검증한다(`security.md` "서비스 간 내부 전용 엔드포인트" 참고). auth-service의 `POST /api/v1/auth/signup`에서만 호출한다.

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `accountId` | UUID | Y | auth-service가 발급한 계정 ID |
| `name` | String | Y | 실명 |
| `phoneNumber` | String | Y | 휴대전화번호 (본인인증 성공 여부와 무관하게 입력값 그대로 저장) |
| `agreedTerms` | Array | Y | 약관 동의 목록 `[{ "code": "SERVICE_USE", "agreed": true }, ...]` |
| `address` | Object | N | 선택 입력 주소 |

Response Body

```json
{
  "memberId": "018e1234-abcd-7xxx-xxxx-xxxxxxxxxxxx",
  "nickname": null,
  "isSeller": true,
  "isBuyer": true,
  "createdAt": "2026-08-26T10:00:00"
}
```

Validation / Business Rules

- `accountId`는 auth-service가 발급한 값만 신뢰(위 내부 전용 방어 참고).
- 회원가입 시 구매자·판매자 권한 모두 부여(확정 — PM 확인 완료).
- 필수 약관 미동의 시 400.
- 생성 실패 시 auth-service에 실패 응답 반환(계정 생성 롤백 유도).
- 본인인증(CI/DI) 관련 필드는 받지 않는다 — auth-service 소관이며 auth-service도 현재 미사용 상태.

---

### 내 프로필 조회 (MEMBER-004 관련)

```
GET /api/v1/members/me
```

Auth Required: **O**

Request Body: 없음 (`@LoginUser`로 주입된 `CurrentUser`로 사용자 식별)

Response Body

```json
{
  "memberId": "018e1234-abcd-7xxx-xxxx-xxxxxxxxxxxx",
  "name": "홍길동",
  "nickname": "응원왕",
  "phoneNumber": "010-1234-5678",
  "isSeller": true,
  "currentMode": "BUYER"
}
```

Validation / Business Rules

- 인증된 사용자 본인의 프로필만 조회 가능.
- 비밀번호·계정 자격증명 관련 필드는 포함하지 않음(auth-service 소관).
- `currentMode`는 `members` 테이블 컬럼이 아니라 세션/토큰 클레임에서 파생되는 값(정확한 저장 계층 미확정).

---

### 구매자/판매자 모드 전환 (MEMBER-004)

```
PATCH /api/v1/members/me/mode
```

Auth Required: **O**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `mode` | Enum | Y | `BUYER` \| `SELLER` |

Response Body

```json
{
  "mode": "SELLER"
}
```

Validation / Business Rules

- 서버 세션/토큰 기준 본인 계정만 전환 가능. 클라이언트가 보낸 권한값은 신뢰하지 않음.
- 회원가입 시 구매자·판매자 권한이 모두 부여되므로 별도 자격 확인 없이 모드 값만 전환(확정).
- `members` 테이블에 이 값을 영속화하지 않는다.

---

### 찜 등록 (MEMBER-005)

```
PUT /api/v1/wishes/{projectId}
```

Auth Required: **O**

Request Body: 없음

Response Body

```json
{
  "projectId": 123,
  "wished": true
}
```

Validation / Business Rules

- **Idempotent.** `INSERT ... ON CONFLICT (member_id, project_id) DO NOTHING`으로 처리 — 이미 찜한 프로젝트를 다시 요청해도 에러 없이 200과 현재 상태를 반환한다.
- 등록 시 `ProjectWished` 이벤트 발행(project-service, search-service 구독용).

---

### 찜 해제 (MEMBER-005)

```
DELETE /api/v1/wishes/{projectId}
```

Auth Required: **O**

Request Body: 없음

Response Body: 없음 (204 No Content)

Validation / Business Rules

- **Idempotent.** 찜하지 않은 프로젝트에 대한 해제 요청도 204로 응답(이미 목표 상태이므로 성공 취급).
- 해제 시 `ProjectUnwished` 이벤트 발행.

---

### 내 찜 목록 조회 (MEMBER-006)

```
GET /api/v1/wishes
```

Auth Required: **O**

Query Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | Int | N | 페이지 번호 (기본값 0) |
| `size` | Int | N | 페이지당 개수 (기본값 20) |

Response Body

```json
{
  "content": [
    {
      "projectId": 123,
      "projectTitle": "무선 이어폰 프로젝트",
      "projectThumbnailUrl": "https://cdn.fundit.com/projects/123/thumb.jpg",
      "createdAt": "2026-08-20T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

Validation / Business Rules

- 본인 찜 목록만 조회 가능.
- `projectTitle`/`projectThumbnailUrl`은 `wishes` 테이블에 저장된 스냅샷 컬럼을 그대로 반환한다(catalog-service를 실시간 호출하지 않음). catalog-service가 발행하는 프로젝트 변경 이벤트를 구독해 동기화하므로, 프로젝트 정보가 바뀐 직후 아주 짧은 시간(최종적 일관성) 동안은 옛 값이 보일 수 있다.

---

### 배송지 목록 조회 (MEMBER-009)

```
GET /api/v1/addresses
```

Auth Required: **O**

Request Body: 없음

Response Body

```json
[
  {
    "id": 1,
    "recipientName": "홍길동",
    "phoneNumber": "010-1234-5678",
    "zipcode": "12345",
    "addressLine1": "서울시 ...",
    "addressLine2": "101동 101호",
    "isDefault": true
  }
]
```

Validation / Business Rules

- 본인 배송지만 조회 가능.

---

### 배송지 등록 (MEMBER-009)

```
POST /api/v1/addresses
```

Auth Required: **O**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `recipientName` | String | Y | 수령인 이름 |
| `phoneNumber` | String | Y | 수령인 연락처 |
| `zipcode` | String | Y | 우편번호 |
| `addressLine1` | String | Y | 기본 주소 (도로명주소 API에서 선택된 값을 프론트가 그대로 전달) |
| `addressLine2` | String | N | 상세 주소 |
| `isDefault` | Boolean | N | 기본 배송지 여부 (기본값 false) |

Response Body

```json
{
  "id": 2,
  "recipientName": "홍길동",
  "isDefault": false
}
```

Validation / Business Rules

- `addressLine1`은 프론트엔드가 도로명주소 API로 직접 검색해 채운 값을 그대로 받는다.
- 필수값 누락 시 400.
- 개인정보(주소)는 저장·전송 시 암호화.

---

## 반영 이력

- **[확정] 내부 전용 엔드포인트 방어**: 게이트웨이 라우팅 제외(네트워크 격리) + `X-Internal-Api-Key` 공유 시크릿 헤더 조합.
- **[확정] 권한 부여 범위**: 회원가입 시 구매자·판매자 권한 모두 부여, PM 확인 완료.
- **[확정] 찜 목록 프로젝트 정보**: `wishes` 테이블에 스냅샷 컬럼 추가(SQL 스키마 반영 완료), catalog-service 이벤트 구독으로 동기화.
- **[확정, 2026-09-03] MVP 범위**: MEMBER-003(소셜가입)/007(팔로우)/008(리워드알림) 엔드포인트를 이 문서에서 제거, `MvpImplementationSummary.md`로 이동.
- **[확정, 2026-09-03] 스키마 정리**: 본인인증·판매자심사 관련 컬럼 제외, `current_mode`는 세션/토큰 클레임으로 관리(DB 미저장).

## ⚠️ 남은 확인 필요 사항

- **catalog-service → member-service 이벤트 스키마**: 필드명·발행 시점 미정.
- **`currentMode`의 정확한 저장 위치**: 세션/토큰 클레임까지는 확정, JWT claim인지 별도 세션스토어인지는 미확정.