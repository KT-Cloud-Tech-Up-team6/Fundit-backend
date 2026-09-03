# member-service — MVP Implementation Summary

이 문서는 member-service의 **MVP 범위 밖(후순위) 기능의 유일한 소스**입니다. `member-functional-spec.md`·`member-domain-api-spec.md`에는 아래 내용이 존재하지 않습니다 — 재개 시 이 문서 내용을 그대로 두 문서로 옮기면 됩니다.

## 남은 것 (TODO, 2026-09-03 기준 — 설계 확정 시점, 코드 착수 전)

### 1. 메이커 팔로우/언팔로우 (MEMBER-007)

**후순위 사유**: `follows` 테이블 자체가 스키마에 없어 신규 생성 필요.

**기능 스펙**
| 항목 | 내용 |
| --- | --- |
| 화면코드 | FL_B_PJ_01_03 |
| 도메인 | 소비자 / 프로젝트 상세 |
| Actor | 구매자 |
| 원안 우선순위 | P2 |
| 설명 | 관심 있는 판매자(메이커)를 팔로우한다 |
| 비즈니스 룰 | 팔로우 관계 저장/삭제 |
| Request | sellerId |
| Response | 팔로우 상태 |
| 예외 | 본인을 팔로우하려는 시도(본인이 판매자인 경우) → 400 |
| 보안 | [S4] 본인 계정 기준 처리 |

**API 스펙**
```
PUT /api/v1/follows/{sellerId}
```
Auth Required: **O** / Request Body: 없음

Response Body
```json
{ "sellerId": "018e5678-abcd-7xxx-xxxx-xxxxxxxxxxxx", "following": true }
```
- **Idempotent.** `INSERT ... ON CONFLICT DO NOTHING`으로 중복 팔로우 요청도 에러 없이 200 처리.
- 본인을 팔로우하려는 시도(본인이 판매자인 경우) → 400.

```
DELETE /api/v1/follows/{sellerId}
```
Auth Required: **O** / Response: 없음(204 No Content)
- **Idempotent.** 팔로우하지 않은 상대에 대한 언팔로우 요청도 204로 응답.

**설계 메모**: 팔로우 대상(seller)도 결국 `members` 테이블의 한 행이므로, `wishes.project_id`(타 서비스 참조, FK 아님)와 달리 **같은 DB 내 참조라 `follows.seller_id`에 FK를 걸 수 있다.**

---

### 2. 리워드 품절 알림 신청 (MEMBER-008)

**후순위 사유**: `reward_alerts` 테이블 자체가 스키마에 없어 신규 생성 필요.

**기능 스펙**
| 항목 | 내용 |
| --- | --- |
| 화면코드 | FL_B_PY_01_01 |
| 도메인 | 소비자 / 펀딩 참여·결제 |
| Actor | 구매자 |
| 원안 우선순위 | P1 |
| 설명 | 품절된 리워드의 재입고/재오픈 알림을 신청한다 |
| 비즈니스 룰 | 알림 신청 이력 저장 |
| Request | rewardId |
| Response | 신청 결과 |
| 예외 | - |
| 보안 | [S4] 본인 계정 기준 신청 처리 |

**API 스펙**
```
POST /api/v1/reward-alerts
```
Auth Required: **O**

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `rewardId` | Long | Y | 알림 신청할 리워드 ID |

Response Body
```json
{ "message": "정상 처리되었습니다." }
```
- 본인 계정 기준으로만 신청.
- 이미 신청한 리워드 중복 신청 시 idempotent 처리(찜/팔로우와 동일 원칙).
- 재입고/재오픈 시 notification-service가 이 신청 내역을 구독해 알림 발송.

**설계 메모**: `reward_id`는 project-service 참조라 FK 아님.

---

### 3. 소셜가입 (MEMBER-003, `POST /members/social`)

**후순위 사유**: auth-service의 AUTH-002/008(소셜 로그인/가입) 자체가 미구현·범위 밖이라 이 엔드포인트는 호출될 일이 없음(2026-09-03 확정).

**기능 스펙**
| 항목 | 내용 |
| --- | --- |
| 화면코드 | FL_C_ME_01_03 |
| 도메인 | 공통 / 회원가입 |
| Actor | 시스템(auth-service 내부 호출) |
| 원안 우선순위 | P1 |
| 설명 | auth-service로부터 소셜 계정 생성 완료 통지를 받아 회원 프로필을 생성한다 |
| 비즈니스 룰 | accountId 기준 프로필 레코드 생성(소셜 제공자에서 받은 이름·이메일 활용), 구매자·판매자 권한 모두 부여, 부족한 필수정보 반영 |
| Request(원안) | accountId, 이름, 이메일, (부족시)추가정보, 약관동의 목록 |
| Response | 생성된 memberId, 프로필 정보 |
| 예외 | 프로필 생성 실패 → auth-service에 실패 응답 반환 |
| 보안 | [S1·S2·S9] accountId는 auth-service가 발급한 값만 신뢰 / 저장 시 암호화(S9) / 입력값 검증·바인딩(S1·S2) |

**API 스펙(원안, 재개 시 재검토 필요)**
```
POST /api/v1/members/social
```
Auth Required: **X** — 내부 전용(auth-service의 `POST /api/v1/auth/signup/social`에서만 호출)

Request Body(원안)

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `accountId` | UUID | Y | auth-service가 발급한 계정 ID |
| `name` | String | Y | 제공자 응답 또는 추가 입력으로 채워진 이름 |
| `email` | String | Y | 소셜 제공자로부터 받은 이메일 |
| `agreedTerms` | Array | Y | 약관 동의 목록 |

Response Body
```json
{
  "memberId": "018e5678-abcd-7xxx-xxxx-xxxxxxxxxxxx",
  "nickname": null,
  "isSeller": true,
  "isBuyer": true,
  "createdAt": "2026-08-26T10:00:00"
}
```

**설계 메모**: **`email` 필드는 member-service 스키마에 저장하지 않는 것으로 확정**(auth-service 소관). 재개 시 Request에서 `email`을 완전히 제거할지, 받되 폐기할지 결정 필요.

일반가입과 소셜가입을 하나로 합치지 않고 나눈 이유(기존 결정 유지): 모니터링(로그만 보고 가입 유형별 통계 파악), 게이트웨이에서 특정 유형만 차단하는 유연성, 조건부 필수 필드 없는 깔끔한 스키마 — 엔드포인트 하나로 관리하는 편의보다 이 세 이점이 크다고 판단. auth 도메인의 회원가입 엔드포인트 분리와 동일한 논리.

---

### 4. 닉네임 수정

**후순위 사유**: `GET /members/me` 응답엔 `nickname`이 있지만 이를 설정/수정하는 엔드포인트가 어떤 명세서에도 없었음. 컬럼(`members.nickname`, nullable)은 이미 존재하므로 스키마 변경 없이 API만 추가하면 됨.

**남은 일**: `PATCH /api/v1/members/me/nickname` 정도의 엔드포인트 스펙을 처음부터 새로 작성해야 함(원안 자체가 없었음).

---

### 5. AUTH-009(이메일 찾기) 연동용 member 내부 조회 엔드포인트

**후순위 사유**: auth-service 쪽 AUTH-009 자체가 "프로덕트 우선순위 조정으로 후순위 확정"(auth-service 기준 2026-08-31)된 상태.

**필요한 것**: auth-service CLAUDE.md가 전제하는 흐름 — "① auth-service가 member-service에 '이 휴대폰번호의 계정 ID'를 조회 → ② 반환된 UUID로 auth-service 자체 accounts 테이블에서 이메일 조회" — 를 지원하는 내부 전용 엔드포인트(예: `GET /api/v1/members/internal/lookup?phoneNumber=...` 형태, 이름·경로는 재개 시 확정). `POST /members`와 동일한 내부 전용 방어(게이트웨이 제외 + `X-Internal-Api-Key`) 적용 필요. `phone_number` 컬럼에 조회용 인덱스 추가 검토.

**남은 일**: 엔드포인트 스펙 자체를 처음부터 작성해야 함(원안 없었음). AUTH-010(비밀번호 재설정)도 동일한 "이름+전화번호 → 조회" 패턴이라 재사용 가능성 있음 — 재개 시 함께 검토.

---

### 6. 본인인증(CI/DI) 관련 필드

**후순위 사유**: auth-service도 현재 미사용 상태(`IdentityVerificationStore.VerifiedIdentity`에 필드 없음, auth-service 기준 2026-08-31).

**남은 일**: 1인 1계정 중복가입 방지가 실제로 필요해지는 시점에 auth-service 쪽 필드 추가와 동시에 member-service 반영 여부 재검토. (참고: PRD의 "본인인증의 통신사 PASS는 모바일 웹 앱 스킴으로 연동" 부분과 CI/DI 기반 중복가입 방지 설계 자체는 앞뒤가 맞음 — SMS 대체 인증 경로로도 CI/DI가 나오는지는 별도 확인 필요.)

---

### 7. 회원 탈퇴 플로우

`deleted_at` 컬럼은 있으나 이를 채우는 쓰기 경로(API/배치)가 아직 어떤 명세서에도 없음. auth-service 쪽 AUTH-012/013(고아 계정 정리 배치)과의 연동 여부도 함께 확인 필요.

**남은 일**: 엔드포인트/배치 스펙 자체를 처음부터 작성해야 함(원안 없었음).

---

## 설계 확정 완료 (스키마에서 제거/변경 반영 — 실제 DDL 파일에도 반영할 것)

- `current_mode` 저장 컬럼 → **불필요**. 구매자/판매자를 별도로 지정하지 않는 방식으로 확정(가입 시 둘 다 자동 부여). 화면 모드는 세션/토큰 클레임 수준에서만 다뤄짐.
- `is_foreigner`/`di_hash`/`phone_verified_at` → **제거**. 본인인증 관련은 전부 auth-service 소관으로 이관. `phone_number`는 인증 여부와 무관하게 회원가입 시 입력값 그대로 저장.
- `business_type`/`business_info`/`seller_verified_at` → **제거**. PRD 서비스 경계표(member-service = 프로필/모드/약관/찜/팔로우/배송지)에 판매자 심사·사업자정보가 없음 — member-service 소관 아님.

## 참고 — auth-service 쪽 관련 진행 상황 (auth-service TODO 기준 2026-08-31~09-03)

- member-service가 아직 레포에 없어(코드 미착수) auth-service의 회원가입 호출은 항상 503(`DEPENDENCY_FAILURE`)으로 떨어지고 보상 트랜잭션(계정 삭제)이 실행되는 게 현재는 의도된 동작 — member-service 구현 후 AUTH-007 엔드투엔드 재검증 필요.
- auth-service `SignupService`의 이메일 중복 체크에 TOCTOU 이슈(동시 가입 시 409 대신 500)가 있다고 알려져 있음. member-service엔 직접 영향 없지만, 회원가입 전체 흐름 통합테스트를 짤 때 이 케이스도 함께 고려할 것.

## 정책값 확인 필요

- `GET /members/me`의 `currentMode` 응답값이 실제로 세션/토큰 클레임 중 어디서 파생되는지(JWT claim vs 별도 세션스토어) 확정 필요.