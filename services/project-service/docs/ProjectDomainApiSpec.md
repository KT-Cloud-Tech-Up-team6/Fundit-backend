## 엔드포인트 목록

| # | Method | Path | 설명 | 인증 | 관련 기능 ID |
| --- | --- | --- | --- | --- | --- |
| 1 | GET | `/api/v1/projects` | 프로젝트 목록 조회(판매자) | O (판매자) | PROJECT-001 |
| 2 | POST | `/api/v1/projects` | 신규 프로젝트 생성 | O (판매자) | PROJECT-003 |
| 3 | DELETE | `/api/v1/projects/{projectId}` | 프로젝트 삭제 | O (판매자) | PROJECT-002 |
| 4 | PATCH | `/api/v1/projects/{projectId}/basic-info` | 프로젝트 기본정보 등록/수정 | O (판매자) | PROJECT-004 |
| 5 | POST | `/api/v1/projects/{projectId}/privacy-consent` | 개인정보 수집 동의 처리 | O (판매자) | PROJECT-005 |
| 6 | POST | `/api/v1/projects/{projectId}/submit` | 프로젝트 심사 제출 | O (판매자) | PROJECT-029 |
| 7 | POST | `/api/v1/admin/projects/{projectId}/review-decision` | 프로젝트 심사 처리(승인/반려) | O (운영자/관리자) | PROJECT-030 |
| 8 | PATCH | `/api/v1/projects/{projectId}/story` | 프로젝트 소개 콘텐츠 등록 | O (판매자) | PROJECT-006 |
| 9 | POST | `/api/v1/projects/{projectId}/rewards` | 리워드 등록 | O (판매자) | PROJECT-007 |
| 10 | PATCH | `/api/v1/rewards/{rewardId}` | 리워드 수정 | O (판매자) | PROJECT-007 |
| 11 | DELETE | `/api/v1/rewards/{rewardId}` | 리워드 삭제 | O (판매자) | PROJECT-007 |
| 12 | PUT | `/api/v1/rewards/{rewardId}/disclosure` | 리워드 정보 제공 고시 등록 | O (판매자) | PROJECT-008 |
| 13 | PATCH | `/api/v1/rewards/{rewardId}/refund-policy` | 환불정책 특이사항 등록 | O (판매자) | PROJECT-009 |
| 14 | GET | `/api/v1/projects/{projectId}/rewards` | 리워드/옵션 조회 및 재고 확인(소비자) | X (공통) | PROJECT-028 |
| 15 | GET | `/api/v1/projects/{projectId}/rewards/disclosures` | 리워드 법정고시정보 조회(소비자) | X (공통) | PROJECT-027 |
| 16 | POST | `/api/v1/projects/{projectId}/notices` | 새소식 등록(판매자) | O (판매자) | PROJECT-010 |
| 17 | GET | `/api/v1/projects/{projectId}/notices` | 새소식 목록 조회(소비자) | X (공통) | PROJECT-022 |
| 18 | POST | `/api/v1/notices/{noticeId}/comments` | 새소식 댓글 등록 | O (구매자) | PROJECT-023 |
| 19 | GET | `/api/v1/notices/{noticeId}/comments` | 새소식 댓글 목록 조회 | X (공통) | PROJECT-023 |
| 20 | POST | `/api/v1/projects/{projectId}/community/posts` | 커뮤니티 질문/응원 등록(소비자) | O (구매자) | PROJECT-024 |
| 21 | GET | `/api/v1/projects/{projectId}/community/posts` | 커뮤니티 게시글 목록 조회(판매자/소비자 공용) | 선택 (미로그인도 조회 가능, 미답변 필터는 판매자 전용) | PROJECT-017, PROJECT-025 |
| 22 | POST | `/api/v1/community/posts/{postId}/answer` | 커뮤니티 답변 등록/수정 | O (판매자) | PROJECT-018 |
| 23 | POST | `/api/v1/projects/{projectId}/ai/funding-story/sessions` | 펀딩스토리 AI — 정보입력/생성요청 | O (판매자) | PROJECT-011 |
| 24 | GET | `/api/v1/ai/funding-story/sessions/{sessionId}` | 펀딩스토리 AI — 결과 조회 | O (판매자) | PROJECT-012 |
| 25 | PATCH | `/api/v1/ai/funding-story/sessions/{sessionId}/apply` | 펀딩스토리 AI — 결과 반영 | O (판매자) | PROJECT-012 |
| 26 | GET | `/api/v1/projects/{projectId}/preview` | 프로젝트 미리보기 조회(판매자) | O (판매자) | PROJECT-013 |
| 27 | GET | `/api/v1/projects/{projectId}` | 프로젝트 상세정보 조회(공개) | X (공통) | PROJECT-020 |
| 28 | GET | `/api/v1/sellers/{sellerId}` | 판매자 정보/이력 조회 | X (공통) | PROJECT-021 |
| 29 | GET | `/api/v1/projects/{projectId}/refund-policy` | 환불정책/환불불가유형 조회 | X (공통) | PROJECT-026 |
| 30 | POST | `/api/v1/projects/{projectId}/live-verifications` | LIVE검증 콘텐츠 등록(판매자) | O (판매자) | PROJECT-014 |
| 31 | PATCH | `/api/v1/live-verifications/{id}` | LIVE검증 콘텐츠 수정 | O (판매자) | PROJECT-014 |
| 32 | DELETE | `/api/v1/live-verifications/{id}` | LIVE검증 콘텐츠 삭제 | O (판매자) | PROJECT-014 |
| 33 | GET | `/api/v1/projects/{projectId}/live-verifications` | 방송종료 후 LIVE검증 질문/답변 조회(소비자) | X (공통) | PROJECT-019 |
| 34 | GET | `/api/v1/projects/{projectId}/funding-status` | 펀딩 현황 조회(판매자) | O (판매자) | PROJECT-015 |
| 35 | GET | `/api/v1/projects/{projectId}/wish-stats` | 찜·알림신청 건수 조회(판매자용) | O (판매자) | PROJECT-016 |

---

## 상세 명세

### 1. 프로젝트 목록 조회(판매자)

```
GET /api/v1/projects
```

**Auth Required**: O (판매자)

**Request**: **Query Parameter**
- `status` (선택): `DRAFT`\|`PENDING_REVIEW`\|`ONGOING`\|`SUCCEEDED`\|`FAILED`
- `page`, `size` (선택, 기본 0/20)

**Response Body**

```json
{
  "content": [
    {
      "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",
      "projectDisplayCode": "F0000123",
      "title": "세상에 없는 프라이팬",
      "thumbnailUrl": "https://cdn.example.com/p/123/thumb.jpg",
      "status": "ONGOING",
      "createdAt": "2026-08-20T10:00:00",
      "fundingDeadline": "2026-09-30T23:59:59"
    }
  ],
  "page": 0, "size": 20, "totalElements": 5, "totalPages": 1, "hasNext": false
}
```

**Validation / Business Rules**

- `@LoginUser`로 주입된 `CurrentUser`를 `seller_id`로 하여 본인 프로젝트만 조회(PRD 3.1, S4).
- `status` 파라미터 미지정 시 전체 상태 반환, 프론트에서 준비중/진행중/종료 탭으로 재구성(PRD 3.1.4).
- 결과 없음 → `content: []` (Empty State는 프론트 처리, 별도 에러 아님).

---

### 2. 신규 프로젝트 생성

```
POST /api/v1/projects
```

**Auth Required**: O (판매자)

**Request**: 없음 (`@LoginUser`로 주입된 `CurrentUser`를 `seller_id`로 사용)

**Response Body**

```json
{
  "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",
  "status": "DRAFT"
}
```

**Validation / Business Rules**

- 로그인 사용자를 `seller_id`로 하는 `projects` 행을 `status=DRAFT`로 생성(PRD 4.1).
- `public_id`는 서버에서 UUID v7로 생성, 이후 모든 프로젝트 API는 이 값을 경로 파라미터로 사용.
- 생성 직후 클라이언트는 기본정보 등록 화면으로 이동(PRD 4.1.4).
- 생성 실패 시 오류 안내 후 재시도 가능해야 함(PRD 4.1.4).

---

### 3. 프로젝트 삭제

```
DELETE /api/v1/projects/{projectId}
```

**Auth Required**: O (판매자)

**Request**: Path Parameter: `projectId`

**Response Body**

```json
204 No Content
```

**Validation / Business Rules**

- `seller_id` 소유권 검증 후 삭제(S4). 타 판매자 프로젝트 → `403 FORBIDDEN`.
- [정책 확인 필요] 진행 중(`ONGOING`) 프로젝트 삭제 허용 여부 미정 — 가정: `DRAFT` 상태만 허용, 그 외 → `422 BUSINESS_RULE_VIOLATION`.
- 삭제는 소프트 삭제(`deleted_at`)로 처리, 중요 삭제 작업은 감사 로그 기록 권장(S8).

---

### 4. 프로젝트 기본정보 등록/수정

```
PATCH /api/v1/projects/{projectId}/basic-info
```

**Auth Required**: O (판매자)

**Request**: {
"businessType": "SOLE",
"categoryMajor": "테크·가전",
"categoryMinor": "생활가전",
"title": "세상에 없는 프라이팬",
"goalAmount": 5000000
}

**Response Body**

```json
{
  "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",
  "businessType": "SOLE",
  "categoryMajor": "테크·가전",
  "categoryMinor": "생활가전",
  "title": "세상에 없는 프라이팬",
  "goalAmount": 5000000,
  "updatedAt": "2026-09-05T10:00:00"
}
```

**Validation / Business Rules**

- PATCH 부분 업데이트 — 전달된 필드만 갱신, 임시저장 겸용(PRD 4.2.4).
- `goalAmount`는 500,000원 이상이어야 함(DB CHECK `goal_amount >= 500000`) — 미달 시 `400 INVALID_INPUT`.
- `title`은 40자 제한(DB `VARCHAR(40)`).
- `categoryMajor`/`categoryMinor` 조합은 `categories` 테이블에 존재해야 함(FK) — 존재하지 않는 조합 → `400 INVALID_INPUT`.
- 소유권(`seller_id`) 검증 후에만 수정 가능, 타 판매자 프로젝트 접근 시 `403 FORBIDDEN`(S4).

---

### 5. 개인정보 수집 동의 처리

```
POST /api/v1/projects/{projectId}/privacy-consent
```

**Auth Required**: O (판매자)

**Request**: { "agreed": true }

**Response Body**

```json
{ "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f", "consentedAt": "2026-09-05T10:00:00" }
```

**Validation / Business Rules**

- `agreed=false` 또는 미동의 시 다음 단계(기본정보 이후) 진행 차단 → `422 BUSINESS_RULE_VIOLATION`(PRD 4.2.4).
- 동의 이력은 법적 근거자료이므로 위변조 방지 저장, 삭제 불가(S9).
- 본인 프로젝트에 대한 동의만 처리 가능(S4).

---

### 6. 프로젝트 심사 제출

```
POST /api/v1/projects/{projectId}/submit
```

**Auth Required**: O (판매자)

**Request**: 없음

**Response Body**

```json
{ "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f", "status": "PENDING_REVIEW" }
```

**Validation / Business Rules**

- 필수 작성 항목(소개·리워드 1개 이상·환불정책 특이사항·개인정보 동의 등) 완료 여부 확인 후 `status=PENDING_REVIEW` 전환.
- 필수 항목 미완료 → `400 INVALID_INPUT`(누락 항목 목록 포함), 제출 불가.
- 제출 시 `project_review_requests` 행 생성(`status=SUBMITTED`), 심사 담당자에게 알림 발행.

---

### 7. 프로젝트 심사 처리(승인/반려)

```
POST /api/v1/admin/projects/{projectId}/review-decision
```

**Auth Required**: O (운영자/관리자)

**Request**: {
"decision": "APPROVED",
"rejectReason": null
}

**Response Body**

```json
{
  "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",
  "status": "ONGOING"
}
```

**Validation / Business Rules**

- `decision=APPROVED` → `project_review_requests.status=APPROVED`, `projects.status=ONGOING`(공개), `funding_start_at`/`funding_deadline` 확정.
- `decision=REJECTED` → `rejectReason` 필수(미입력 시 `400 INVALID_INPUT`), `projects.status=DRAFT`로 되돌려 재제출 가능하게 처리, 판매자 알림 발행.
- 관리자 권한 서버 검증 필수(S4), 승인·반려 이력은 감사 로그로 기록(S8).

---

### 8. 프로젝트 소개 콘텐츠 등록

```
PATCH /api/v1/projects/{projectId}/story
```

**Auth Required**: O (판매자)

**Request**: {
"title": "세상에 없는 프라이팬",
"coverImageUrl": "https://cdn.example.com/p/123/cover.jpg",
"introContent": [
{ "type": "TEXT", "value": "..." },
{ "type": "IMAGE", "value": "https://cdn.example.com/p/123/1.jpg" },
{ "type": "VIDEO_URL", "value": "https://youtube.com/..." }
]
}

**Response Body**

```json
{ "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f", "updatedAt": "2026-09-05T10:00:00" }
```

**Validation / Business Rules**

- `title` 40자 제한(DB 컬럼 제약과 동일).
- `coverImageUrl`은 10MB 이하 JPG/JPEG/PNG 1개만 허용, 확장자 화이트리스트 검증 및 저장 파일명은 추측 불가능한 값으로 변경(S5).
- `introContent`의 텍스트 항목은 소비자 화면에 그대로 노출되므로 출력 인코딩 적용(XSS 방지, S2).
- 임시저장 겸용이며 부분 필드만 전달해도 저장 가능.

---

### 9. 리워드 등록

```
POST /api/v1/projects/{projectId}/rewards
```

**Auth Required**: O (판매자)

**Request**: {
"name": "얼리버드 패키지",
"description": "...",
"imageUrl": "https://cdn.example.com/r/1.jpg",
"price": 39000,
"isLimited": true,
"quantity": 100,
"isEarlyBird": true,
"options": [
{ "groupName": "색상", "values": ["화이트", "블랙"] }
]
}

**Response Body**

```json
{
  "rewardId": 1,
  "rewardDisplayCode": "R0000001",
  "name": "얼리버드 패키지",
  "price": 39000,
  "isLimited": true,
  "quantity": 100,
  "hasOption": true
}
```

**Validation / Business Rules**

- 필수값(`name`,`price`,`quantity`\[`isLimited=true`인 경우\]) 누락 → `400 INVALID_INPUT`(PRD 4.1.4).
- `isLimited=true`이면 `quantity` 필수(0 이상), `isLimited=false`이면 `quantity`는 null이어야 함(DB CHECK `chk_rewards_quantity`).
- `options` 전달 시 `has_option=true`로 저장하고 `reward_option_groups`/`reward_option_values` 2단 구조로 생성.
- 리워드 생성/수정 시 order-service의 재고 원장(`inventories.available_stock`)에 `quantity` 값을 동기화하는 이벤트를 발행해야 함(ORDER-012 연동, `RewardCreated`).
- 소유권(`seller_id`) 검증(S4), `name`/`description`은 출력 인코딩 적용(S2).

---

### 10. 리워드 수정

```
PATCH /api/v1/rewards/{rewardId}
```

**Auth Required**: O (판매자)

**Request**: 등록과 동일한 필드 중 변경할 필드만 부분 전달

**Response Body**

```json
등록 응답과 동일 구조
```

**Validation / Business Rules**

- 소유권 검증: 리워드가 속한 프로젝트의 `seller_id`가 본인인지 확인(S4).
- `quantity` 변경 시 order-service `inventories` 동기화 이벤트(`RewardUpdated`) 발행 필요(ORDER-012 연동).
- 이미 판매(주문)가 발생한 리워드의 `price` 인하/인상 등 정책은 [정책 확인 필요].

---

### 11. 리워드 삭제

```
DELETE /api/v1/rewards/{rewardId}
```

**Auth Required**: O (판매자)

**Request**: Path Parameter: `rewardId`

**Response Body**

```json
204 No Content
```

**Validation / Business Rules**

- 소프트 삭제(`deleted_at`) 처리 — 삭제된 리워드는 소비자 응답에서 제외.
- 소유권 검증(S4), 이미 주문(`funding_line_items`)이 존재하는 리워드 삭제 가능 여부는 [정책 확인 필요].

---

### 12. 리워드 정보 제공 고시 등록

```
PUT /api/v1/rewards/{rewardId}/disclosure
```

**Auth Required**: O (판매자)

**Request**: {
"categoryType": "COSMETIC",
"disclosure": {
"품명및모델명": "...",
"제조자": "...",
"제조국": "대한민국",
"크기용량형태": "...",
"AS책임자전화번호": "1588-0000"
}
}

**Response Body**

```json
{ "rewardId": 1, "categoryType": "COSMETIC", "disclosure": { "...": "..." } }
```

**Validation / Business Rules**

- `categoryType`에 따라 필수 입력 항목 구성이 달라짐(화장품/식품/전자제품 등 템플릿, PRD 12.7.3).
- 미입력 항목이 있어도 저장은 허용하되, 프로젝트 심사 제출(PROJECT-029) 시점에 검수 대상이 됨.
- `disclosure`는 JSONB로 저장, 품목 유형별 템플릿 검증은 애플리케이션 레이어에서 수행.
- 소유권 검증(S4), 출력 인코딩 적용(S2).

---

### 13. 환불정책 특이사항 등록

```
PATCH /api/v1/rewards/{rewardId}/refund-policy
```

**Auth Required**: O (판매자)

**Request**: { "simpleRefundDisabled": true }

**Response Body**

```json
{ "rewardId": 1, "simpleRefundDisabled": true }
```

**Validation / Business Rules**

- 체크 시 `rewards.simple_refund_disabled=true`로 설정(각인·주문제작·신선식품 등, PRD 5.1.4.1).
- 이 값은 소비자 화면(환불정책 탭, PROJECT-026) 및 리워드 선택 화면에 '단순변심 환불 불가' 배지로 노출됨.
- 소유권 검증(S4).

---

### 14. 리워드/옵션 조회 및 재고 확인(소비자)

```
GET /api/v1/projects/{projectId}/rewards
```

**Auth Required**: X (공통)

**Request**: Path Parameter: `projectId`

**Response Body**

```json
[
  {
    "rewardId": 1,
    "rewardDisplayCode": "R0000001",
    "name": "얼리버드 패키지",
    "price": 39000,
    "isEarlyBird": true,
    "isLimited": true,
    "remainingStock": 37,
    "options": [
      { "groupId": 10, "groupName": "색상", "values": [
          { "valueId": 100, "value": "화이트" },
          { "valueId": 101, "value": "블랙" } ] }
    ],
    "soldOut": false
  }
]
```

**Validation / Business Rules**

- 리워드 구성·가격·옵션은 project-service가 직접 응답, `remainingStock`은 **order-service `inventories` 조회 API를 실시간 호출**해 조합(재고 원장은 order-service 소유, PROJECT-028 검토의견 참고).
- 삭제된 리워드(`deleted_at` not null)는 응답에서 제외.
- `remainingStock=0` → `soldOut: true`로 표시, 프론트는 '알림 신청' 버튼으로 대체(PRD 13.1.4).
- order-service 재고 API 장애 시 `remainingStock`을 null로 반환하고 별도 안내 필요[가정].

**추가 에러 코드**

| 에러 코드 | HTTP | 설명 |
| --- | --- | --- |
| `DEPENDENCY_FAILURE` | 503 | order-service 재고 조회 실패 시 |

---

### 15. 리워드 법정고시정보 조회(소비자)

```
GET /api/v1/projects/{projectId}/rewards/disclosures
```

**Auth Required**: X (공통)

**Request**: Path Parameter: `projectId`

**Response Body**

```json
[
  { "rewardId": 1, "rewardName": "얼리버드 패키지", "categoryType": "COSMETIC",
    "disclosure": { "품명및모델명": "...", "제조국": "대한민국" } }
]
```

**Validation / Business Rules**

- 리워드가 여러 개인 경우 리워드 단위로 반복 노출(PRD 12.7.4).
- 특정 항목 값이 없는 경우 해당 항목은 `null`로 반환, 프론트에서 '정보 없음' 표시.

---

### 16. 새소식 등록(판매자)

```
POST /api/v1/projects/{projectId}/notices
```

**Auth Required**: O (판매자)

**Request**: {
"noticeType": "PRODUCTION_UPDATE",
"title": "생산 진행 상황 안내",
"content": "..."
}

**Response Body**

```json
{ "noticeId": 501, "noticeType": "PRODUCTION_UPDATE", "title": "생산 진행 상황 안내", "createdAt": "2026-09-05T10:00:00" }
```

**Validation / Business Rules**

- `noticeType`은 리워드안내/이벤트/제작과정/발송정보/달성률/교환환불/결제안내/FAQ 중 하나(화이트리스트 검증).
- 필수값 누락 → `400 INVALID_INPUT`.
- `content`는 소비자 화면에 노출되므로 출력 인코딩(XSS 방지, S2) 적용, 소유권 검증(S4).

---

### 17. 새소식 목록 조회(소비자)

```
GET /api/v1/projects/{projectId}/notices
```

**Auth Required**: X (공통)

**Request**: Query: `noticeType`(선택), `sort`=`LATEST`\|`POPULAR`(기본 `LATEST`)

**Response Body**

```json
{
  "content": [
    { "noticeId": 501, "noticeType": "PRODUCTION_UPDATE", "title": "생산 진행 상황 안내", "createdAt": "2026-09-05T10:00:00" }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

**Validation / Business Rules**

- `noticeType` 파라미터는 화이트리스트 검증(동적 정렬/필터 조건 삽입 방지, S1).
- 기본 정렬은 최신순.

---

### 18. 새소식 댓글 등록

```
POST /api/v1/notices/{noticeId}/comments
```

**Auth Required**: O (구매자)

**Request**: { "content": "기대돼요!" }

**Response Body**

```json
{ "commentId": 9001, "noticeId": 501, "content": "기대돼요!", "createdAt": "2026-09-05T10:05:00" }
```

**Validation / Business Rules**

- `content` 500자 제한(DB `VARCHAR(500)`).
- 출력 시 인코딩 적용(S2), 인증된 사용자만 작성 가능(S4).

---

### 19. 새소식 댓글 목록 조회

```
GET /api/v1/notices/{noticeId}/comments
```

**Auth Required**: X (공통)

**Request**: Path Parameter: `noticeId`

**Response Body**

```json
{ "content": [ { "commentId": 9001, "content": "기대돼요!", "createdAt": "2026-09-05T10:05:00" } ], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false }
```

**Validation / Business Rules**

- 삭제된 댓글(`deleted_at` not null)은 응답에서 제외.

---

### 20. 커뮤니티 질문/응원 등록(소비자)

```
POST /api/v1/projects/{projectId}/community/posts
```

**Auth Required**: O (구매자)

**Request**: { "postType": "QUESTION", "content": "배송은 언제쯤 시작되나요?" }

**Response Body**

```json
{ "postId": 7001, "postType": "QUESTION", "content": "배송은 언제쯤 시작되나요?", "createdAt": "2026-09-05T10:10:00" }
```

**Validation / Business Rules**

- `postType`은 `QUESTION`\|`CHEER`만 허용.
- 출력 인코딩 적용(S2), 인증된 사용자만 작성 가능(S4).

---

### 21. 커뮤니티 게시글 목록 조회(판매자/소비자 공용)

```
GET /api/v1/projects/{projectId}/community/posts
```

**Auth Required**: 선택 (미로그인도 조회 가능, 미답변 필터는 판매자 전용)

**Request**: Query: `postType`(선택), `answeredOnly`(선택, 판매자 전용)

**Response Body**

```json
{
  "content": [
    { "postId": 7001, "postType": "QUESTION", "content": "배송은 언제쯤 시작되나요?",
      "answer": null, "createdAt": "2026-09-05T10:10:00" }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

**Validation / Business Rules**

- 동일 엔드포인트를 판매자(PROJECT-017)·소비자(PROJECT-025) 화면에서 공용으로 사용, 판매자 요청 시에만 미답변 여부 필터(`answeredOnly`) 적용 가능(S4로 소유권 확인 후 필터 허용).
- `answer` 필드는 `community_answers`가 있으면 답변 내용을, 없으면 `null` 반환.
- 게시글 내용 출력 시 인코딩 적용(S2).

---

### 22. 커뮤니티 답변 등록/수정

```
POST /api/v1/community/posts/{postId}/answer
```

**Auth Required**: O (판매자)

**Request**: { "content": "다음 주 중 순차 발송 예정입니다." }

**Response Body**

```json
{ "postId": 7001, "answer": { "content": "다음 주 중 순차 발송 예정입니다.", "updatedAt": "2026-09-05T10:20:00" } }
```

**Validation / Business Rules**

- 게시글당 답변 1개(DB `uq_community_answers_post` 유니크 제약) — 재호출 시 기존 답변을 수정(UPSERT).
- 답변 등록/수정 시 작성자에게 온사이트 알림 이벤트 발행(notification-service 연동).
- 본인 소유 프로젝트의 게시글만 답변 가능(S4), 출력 인코딩 적용(S2).

---

### 23. 펀딩스토리 AI — 정보입력/생성요청

```
POST /api/v1/projects/{projectId}/ai/funding-story/sessions
```

**Auth Required**: O (판매자)

**Request**: {
"productDescription": "...",
"productImageUrls": ["https://cdn.example.com/tmp/1.jpg"],
"answers": [
{ "questionId": "Q1", "answer": "타깃은 캠핑 초보자입니다." }
]
}

**Response Body**

```json
{ "sessionId": "018f2c9a-....", "status": "GENERATING" }
```

**Validation / Business Rules**

- 비동기 처리 — 응답은 `sessionId`만 반환하고 실제 생성 결과는 결과조회 API로 폴링(PRD 5.1.4.2).
- 동일 세션에 대한 중복 생성 요청은 차단(`status=GENERATING`인 동안 재요청 시 `409 CONFLICT`).
- 외부 AI 서비스 연동 API Key는 코드와 분리 보관, 요청/응답 검증(S7).
- 제품 이미지 업로드 시 확장자·용량 검증(S5).

---

### 24. 펀딩스토리 AI — 결과 조회

```
GET /api/v1/ai/funding-story/sessions/{sessionId}
```

**Auth Required**: O (판매자)

**Request**: Path Parameter: `sessionId`

**Response Body**

```json
{
  "sessionId": "018f2c9a-....",
  "status": "COMPLETED",
  "additionalQuestions": [],
  "result": {
    "sections": [ { "type": "INTRO", "title": "...", "body": "...", "images": ["..."] } ],
    "imagesSource": [ { "url": "...", "source": "GENERATED" } ],
    "warnings": [ { "field": "body", "reason": "근거 없는 주장으로 식별됨" } ]
  }
}
```

**Validation / Business Rules**

- `status`가 `GENERATING`인 동안은 진행 상태만 반환(`result: null`).
- 생성 실패 시 `status=FAILED`와 함께 재시도 가능 플래그 반환.
- 판매자 입력에 근거가 없는 항목은 `warnings`로 식별해 반환(PRD 5.1.4.2).

---

### 25. 펀딩스토리 AI — 결과 반영

```
PATCH /api/v1/ai/funding-story/sessions/{sessionId}/apply
```

**Auth Required**: O (판매자)

**Request**: { "mode": "OVERWRITE", "edits": [ { "sectionType": "INTRO", "body": "수정된 본문" } ] }

**Response Body**

```json
{ "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f", "appliedAt": "2026-09-05T10:30:00" }
```

**Validation / Business Rules**

- `mode`는 `OVERWRITE`(전체 덮어쓰기) 또는 `COPY`(복사하기) 중 선택(PRD 5.1.4).
- 반영된 내용은 프로젝트 스토리(`PATCH .../story`)에 임시저장되며 이후 이어서 작성 가능.
- 생성 결과는 최종적으로 소비자 화면에 노출되므로 반영 시 출력 인코딩 적용(S2).

---

### 26. 프로젝트 미리보기 조회(판매자)

```
GET /api/v1/projects/{projectId}/preview
```

**Auth Required**: O (판매자)

**Request**: Path Parameter: `projectId`

**Response Body**

```json
공개용 상세조회(#27)와 동일 구조 — 미공개(`DRAFT`/`PENDING_REVIEW`) 상태에서도 조회 가능
```

**Validation / Business Rules**

- 본인 소유 프로젝트만 미리보기 접근 가능, 타 판매자 → `403 FORBIDDEN`(S4).
- 공개 여부와 무관하게 항상 최신 작성 내용을 렌더링용 전체 데이터로 반환.

---

### 27. 프로젝트 상세정보 조회(공개)

```
GET /api/v1/projects/{projectId}
```

**Auth Required**: X (공통)

**Request**: Path Parameter: `projectId`

**Response Body**

```json
{
  "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",
  "title": "세상에 없는 프라이팬",
  "status": "ONGOING",
  "goalAmount": 5000000,
  "fundingStatus": { "currentAmount": 3200000, "achievementRate": 64, "participantCount": 128, "remainingDays": 5 },
  "hasLiveVerification": true,
  "seller": { "sellerId": "...", "displayName": "..." }
}
```

**Validation / Business Rules**

- `status`가 `DRAFT`\|`PENDING_REVIEW`인 미공개 프로젝트 조회 시 `404 NOT_FOUND`(본인이면 미리보기 API 사용).
- 실시간 펀딩현황(`fundingStatus`)은 PROJECT-015와 동일한 집계 데이터 소스 사용.
- `hasLiveVerification`은 `project_notices`\/LIVE검증 콘텐츠 존재 여부 기준 인증뱃지 표시용.

---

### 28. 판매자 정보/이력 조회

```
GET /api/v1/sellers/{sellerId}
```

**Auth Required**: X (공통)

**Request**: Path Parameter: `sellerId`

**Response Body**

```json
{
  "sellerId": "...",
  "businessType": "SOLE",
  "pastProjects": [
    { "projectId": "...", "title": "이전 프로젝트", "status": "SUCCEEDED" }
  ]
}
```

**Validation / Business Rules**

- 사업자정보 중 대표자 개인 연락처 등 개인정보는 공개 범위에서 제외(S9).
- 과거 프로젝트는 공개(`ONGOING`/`SUCCEEDED`/`FAILED`) 상태만 노출.

---

### 29. 환불정책/환불불가유형 조회

```
GET /api/v1/projects/{projectId}/refund-policy
```

**Auth Required**: X (공통)

**Request**: Path Parameter: `projectId`

**Response Body**

```json
{
  "commonPolicy": { "simpleRefundDeadline": "펀딩 마감 전까지", "goalFailedAutoRefund": true },
  "rewardPolicies": [
    { "rewardId": 1, "simpleRefundDisabled": false }
  ]
}
```

**Validation / Business Rules**

- 플랫폼 공통 정책은 서버 상수/설정값으로 고정 제공(판매자 수정 불가).
- 리워드별 `simple_refund_disabled` 값을 결합해 리워드 단위 환불 불가 여부 노출(PROJECT-009 연동).

---

### 30. LIVE검증 콘텐츠 등록(판매자)

```
POST /api/v1/projects/{projectId}/live-verifications
```

**Auth Required**: O (판매자)

**Request**: { "questionSummaryId": "live-q-1", "answer": "네, 방수 기능 있습니다." }

**Response Body**

```json
{ "liveVerificationId": 301, "answer": "네, 방수 기능 있습니다.", "createdAt": "2026-09-05T10:40:00" }
```

**Validation / Business Rules**

- `questionSummaryId`는 live-service가 생성한 질문 요약 참조값(cross-service, FK 아님).
- 답변 텍스트는 소비자 화면(프로젝트 상세 LIVE검증 탭)에 노출되므로 출력 인코딩 적용(S2).
- 본인 소유 프로젝트만 등록 가능(S4).

---

### 31. LIVE검증 콘텐츠 수정

```
PATCH /api/v1/live-verifications/{id}
```

**Auth Required**: O (판매자)

**Request**: { "answer": "수정된 답변" }

**Response Body**

```json
{ "liveVerificationId": 301, "answer": "수정된 답변", "updatedAt": "2026-09-05T10:45:00" }
```

**Validation / Business Rules**

- 소유권 검증(S4), 출력 인코딩 적용(S2).

---

### 32. LIVE검증 콘텐츠 삭제

```
DELETE /api/v1/live-verifications/{id}
```

**Auth Required**: O (판매자)

**Request**: Path Parameter: `id`

**Response Body**

```json
204 No Content
```

**Validation / Business Rules**

- 소유권 검증(S4).

---

### 33. 방송종료 후 LIVE검증 질문/답변 조회(소비자)

```
GET /api/v1/projects/{projectId}/live-verifications
```

**Auth Required**: X (공통)

**Request**: Path Parameter: `projectId`

**Response Body**

```json
{
  "content": [
    { "liveVerificationId": 301, "questionCount": 12, "answer": "네, 방수 기능 있습니다." }
  ]
}
```

**Validation / Business Rules**

- LIVE 미진행 프로젝트는 빈 배열 반환(탭 자체는 프론트에서 미노출 처리, PRD 12.2.4).

---

### 34. 펀딩 현황 조회(판매자)

```
GET /api/v1/projects/{projectId}/funding-status
```

**Auth Required**: O (판매자)

**Request**: Path Parameter: `projectId`

**Response Body**

```json
{
  "currentAmount": 3200000, "achievementRate": 64, "participantCount": 128,
  "openNotifyCount": 40, "wishCount": 210,
  "rewardStats": [ { "rewardId": 1, "purchasedQuantity": 30 } ],
  "remainingDays": 5, "lastSyncedAt": "2026-09-05T00:00:00"
}
```

**Validation / Business Rules**

- order-service가 발행하는 펀딩 집계 이벤트를 구독해 반영(데이터 갱신 주기 1일, PRD 7.1.3).
- 본인 소유 프로젝트만 조회 가능(S4).

---

### 35. 찜·알림신청 건수 조회(판매자용)

```
GET /api/v1/projects/{projectId}/wish-stats
```

**Auth Required**: O (판매자)

**Request**: Path Parameter: `projectId`

**Response Body**

```json
{ "wishCount": 210, "openNotifyCount": 40 }
```

**Validation / Business Rules**

- member-service가 발행하는 `ProjectWished`\/`ProjectUnwished` 이벤트를 구독해 집계 카운트 유지.
- 본인 소유 프로젝트만 조회 가능(S4).

---
