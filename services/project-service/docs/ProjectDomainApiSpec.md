## 엔드포인트 목록

| # | Method | Path | 설명 | 인증 | 관련 기능 ID |
| --- | --- | --- | --- | --- | --- |
| 1 | GET | `/api/v1/projects` | 프로젝트 목록 조회(판매자) | O (판매자) | PROJECT-001 |
| 1-1 | GET | `/api/v1/projects/status-counts` | 프로젝트 상태 그룹별 개수 조회(판매자) | O (판매자) | PROJECT-001 |
| 2 | POST | `/api/v1/projects` | 신규 프로젝트 생성 | O (판매자) | PROJECT-003 |
| 3 | DELETE | `/api/v1/projects/{projectId}` | 프로젝트 삭제 | O (판매자) | PROJECT-002 |
| 4 | PATCH | `/api/v1/projects/{projectId}/basic-info` | 프로젝트 기본정보 등록/수정 | O (판매자) | PROJECT-004 |
| 5 | POST | `/api/v1/projects/{projectId}/privacy-consent` | 개인정보 수집 동의 처리 | O (판매자) | PROJECT-005 |
| 6 | POST | `/api/v1/projects/{projectId}/submit` | 프로젝트 공개(발행) | O (판매자) | PROJECT-029 |
| 8 | PATCH | `/api/v1/projects/{projectId}/story` | 프로젝트 소개 콘텐츠 등록 | O (판매자) | PROJECT-006 |
| 9 | POST | `/api/v1/projects/{projectId}/media/upload-url` | 이미지/영상 업로드 주소 발급(S3 Presigned URL) | O (판매자) | PROJECT-006, PROJECT-007 |
| 10 | POST | `/api/v1/projects/{projectId}/rewards` | 리워드 등록 | O (판매자) | PROJECT-007 |
| 11 | PATCH | `/api/v1/rewards/{rewardId}` | 리워드 수정 | O (판매자) | PROJECT-007 |
| 12 | DELETE | `/api/v1/rewards/{rewardId}` | 리워드 삭제 | O (판매자) | PROJECT-007 |
| 13 | PATCH | `/api/v1/rewards/{rewardId}/refund-policy` | 환불정책 특이사항 등록 | O (판매자) | PROJECT-009 |
| 14 | GET | `/api/v1/projects/{projectId}/rewards` | 리워드/옵션 조회 및 재고 확인(소비자) | X (공통) | PROJECT-028 |
| 14-1 | GET | `/api/v1/projects/{projectId}/rewards/mine` | 리워드 목록 조회(판매자, 공개여부 무관) | O (판매자) | PROJECT-007 |
| 14-2 | GET | `/api/v1/rewards/{rewardId}` | 리워드 상세 조회(소비자) | X (공통) | PROJECT-028 |
| 15 | POST | `/api/v1/projects/{projectId}/notices` | 새소식 등록(판매자) | O (판매자) | PROJECT-010 |
| 16 | GET | `/api/v1/projects/{projectId}/notices` | 새소식 목록 조회(소비자) | X (공통) | PROJECT-022 |
| 16-1 | GET | `/api/v1/notices/{noticeId}` | 새소식 본문 조회(열람·재편집용) | X (공통) | PROJECT-022 |
| 17 | POST | `/api/v1/notices/{noticeId}/comments` | 새소식 댓글 등록 | O (로그인 회원, 구매이력 미검증) | PROJECT-023 |
| 18 | GET | `/api/v1/notices/{noticeId}/comments` | 새소식 댓글 목록 조회 | X (공통) | PROJECT-023 |
| 19 | POST | `/api/v1/projects/{projectId}/community/posts` | 커뮤니티 질문/응원 등록(소비자) | O (로그인 회원, 구매이력 미검증) | PROJECT-024 |
| 20 | GET | `/api/v1/projects/{projectId}/community/posts` | 커뮤니티 게시글 목록 조회(판매자/소비자 공용) | 선택 (미로그인도 조회 가능, 미답변 필터는 판매자 전용) | PROJECT-017, PROJECT-025 |
| 21 | POST | `/api/v1/community/posts/{postId}/answer` | 커뮤니티 답변 등록/수정 | O (판매자) | PROJECT-018 |
| 22 | POST | `/api/v1/ai/sessions` | 정보 수집 세션 생성 | O (판매자) | PROJECT-011 |
| 22-1 | GET | `/api/v1/ai/sessions/latest` | 유효한 최신 세션 복구 | O (판매자) | PROJECT-011 |
| 22-2 | GET | `/api/v1/ai/sessions/{sessionId}` | 정보 수집 세션 조회 | O (판매자) | PROJECT-011 |
| 22-3 | POST | `/api/v1/ai/sessions/{sessionId}/start` | 첫 질문 생성 시작 | O (판매자) | PROJECT-011 |
| 22-4 | POST | `/api/v1/ai/sessions/{sessionId}/messages` | 사용자 메시지 전달 | O (판매자) | PROJECT-011 |
| 22-5 | GET | `/api/v1/ai/chats/{chatId}/events` | 채팅 SSE 중계 | O (판매자) | PROJECT-011 |
| 22-6 | POST | `/api/v1/ai/sessions/{sessionId}/confirm` | 요약 revision 확인 | O (판매자) | PROJECT-011 |
| 23 | POST | `/api/v1/ai/runs` | 전체 상세페이지 생성 | O (판매자) | PROJECT-012 |
| 24 | GET | `/api/v1/ai/runs/{runId}` | BE 소유 생성 상태·결과 조회 | O (판매자) | PROJECT-012 |
| 24-1 | POST | `/internal/ai/media/upload-targets` | AI 최종 PNG 업로드 대상 발급 | 내부 키 | PROJECT-012 |
| 24-2 | POST | `/internal/ai/runs/{runId}/completion` | AI 생성 완료 callback | 내부 키 | PROJECT-012 |
| 25 | GET | `/api/v1/projects/{projectId}/preview` | 프로젝트 미리보기 조회(판매자) | O (판매자) | PROJECT-013 |
| 26 | GET | `/api/v1/projects/{projectId}` | 프로젝트 상세정보 조회(공개) | X (공통) | PROJECT-020 |
| 27 | GET | `/api/v1/sellers/{sellerId}` | 판매자 정보/이력 조회 | X (공통) | PROJECT-021 |
| 28 | GET | `/api/v1/projects/{projectId}/refund-policy` | 환불정책/환불불가유형 조회 | X (공통) | PROJECT-026 |
| 29 | POST | `/api/v1/projects/{projectId}/live-verifications` | LIVE검증 콘텐츠 등록(판매자) | O (판매자) | PROJECT-014 |
| 30 | PATCH | `/api/v1/live-verifications/{id}` | LIVE검증 콘텐츠 수정 | O (판매자) | PROJECT-014 |
| 31 | DELETE | `/api/v1/live-verifications/{id}` | LIVE검증 콘텐츠 삭제 | O (판매자) | PROJECT-014 |
| 32 | GET | `/api/v1/projects/{projectId}/live-verifications` | 방송종료 후 LIVE검증 질문/답변 조회(소비자) | X (공통) | PROJECT-019 |
| 33 | GET | `/api/v1/projects/{projectId}/funding-status` | 펀딩 현황 조회(판매자) | O (판매자) | PROJECT-015 |
| 34 | GET | `/api/v1/projects/{projectId}/wish-stats` | 찜·알림신청 건수 조회(판매자용) | O (판매자) | PROJECT-016 |
| 35 | GET | `/internal/projects/{projectId}` | 내부 프로젝트 스냅샷 조회(Long PK, v1/Kafka 해석용) | 내부 키 (`X-Internal-Api-Key`) | — |
| 36 | GET | `/internal/projects/summaries` | 내부 프로젝트 배치 요약 조회(order-service 주문 목록용) | 내부 키 (`X-Internal-Api-Key`) | — |

> **식별자 계약 (cross-service ID 통일 #69)**: 공개 REST의 `projectId`는 항상 `publicId`(UUID)다. 다운스트림 공개 API도 같은 UUID를 받는다 — order `POST /api/v2/orders`, fulfillment `/api/v2/projects/{projectId}/**`. payment의 `fundingId`는 order-service `orderId`(UUID). 이 서비스의 Long 내부 PK는 `GET /internal/projects/{projectId}`와 Kafka 파티션 키에만 남는다.
>
> PROJECT-008(리워드 정보 제공 고시 등록)·PROJECT-027(리워드 법정고시정보 조회)은 품목마다 필요한 고시 항목이 달라 MVP에서 표준화하기 어려워 **범위 제외됐다**(PM 확정). 관련 엔드포인트(`PUT /api/v1/rewards/{rewardId}/disclosure`, `GET /api/v1/projects/{projectId}/rewards/disclosures`)는 존재하지 않으며, 도메인/DTO/테스트도 모두 제거됐다.
>
> PROJECT-030(프로젝트 심사 처리/승인·반려)은 관리자 승인 단계 자체가 **폐지됐다** — 필수 작성 항목이 채워지면 관리자 승인 없이 바로 공개(ONGOING)로 전환한다(PROJECT-029 참고). 관련 엔드포인트(`POST /api/v1/admin/projects/{projectId}/review-decision`)는 존재하지 않으며, 도메인/DTO/테스트, `project_review_requests` 테이블도 모두 제거됐다.

---

## 상세 명세

### 1. 프로젝트 목록 조회(판매자)

```
GET /api/v1/projects
```

**Auth Required**: O (판매자)

**Request**: **Query Parameter**
- `status` (선택): `DRAFT`\|`ONGOING`\|`SUCCEEDED`\|`FAILED`. 콤마로 구분한 다중값 지원(예: `SUCCEEDED,FAILED`)
- `q` (선택): 제목 부분 일치 검색(대소문자 무시)
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
      "fundingStartAt": "2026-08-21T00:00:00",
      "fundingDeadline": "2026-09-30T23:59:59",
      "goalAmount": 3000000,
      "categoryMajor": "테크·가전",
      "categoryMinor": "생활가전",
      "currentAmount": 1280000,
      "participantCount": 132,
      "achievementRate": 128
    }
  ],
  "page": 0, "size": 20, "totalElements": 5, "totalPages": 1, "hasNext": false
}
```

**Validation / Business Rules**

- `@LoginUser`로 주입된 `CurrentUser`를 `seller_id`로 하여 본인 프로젝트만 조회(PRD 3.1, S4).
- `status` 파라미터 미지정 시 전체 상태 반환, 프론트에서 준비중/진행중/종료 탭으로 재구성(PRD 3.1.4).
- 결과 없음 → `content: []` (Empty State는 프론트 처리, 별도 에러 아님).
- `currentAmount`/`participantCount`/`achievementRate`는 `funding_status_snapshots`를 페이지 단위로 배치 조회해
  채운다(카드 수만큼 단건 호출을 반복하지 않음). 스냅샷이 없는 프로젝트는 0으로 응답.

---

### 1-1. 프로젝트 상태 그룹별 개수 조회(판매자)

```
GET /api/v1/projects/status-counts
```

**Auth Required**: O (판매자)

**Request**: 없음

**Response Body**

```json
{ "ongoing": 3, "draft": 1, "completed": 5 }
```

**Validation / Business Rules**

- 탭(진행중/준비중/완료) 배지에 표시할 개수를 한 번의 호출로 제공한다.
- `ongoing`=ONGOING, `draft`=DRAFT, `completed`=SUCCEEDED+FAILED 합산.

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
- `DRAFT` 상태만 삭제 가능. 그 외 상태 → `422 PROJECT_NOT_DELETABLE`.
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
- `goalAmount`는 500,000원 이상이어야 함(DB CHECK `goal_amount >= 500000`) — 미달 시 `400 GOAL_AMOUNT_TOO_LOW`.
- `title`은 40자 제한(DB `VARCHAR(40)`).
- `categoryMajor`/`categoryMinor` 조합은 `categories` 테이블에 존재해야 함(FK) — 존재하지 않는 조합 → `400 INVALID_CATEGORY`.
- 소유권(`seller_id`) 검증 후에만 수정 가능, 타 판매자 프로젝트 접근 시 `403 FORBIDDEN`(S4).
- 프로젝트가 이미 공개(`ONGOING`/`SUCCEEDED`/`FAILED`) 상태이면 저장 후 `project.updated.v1`을 발행한다(SEARCH-011). DRAFT 수정은 발행하지 않는다.

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

- `agreed=false` 또는 미동의 시 다음 단계(기본정보 이후) 진행 차단 → `422 PRIVACY_CONSENT_REQUIRED`(PRD 4.2.4).
- 동의 이력은 법적 근거자료이므로 위변조 방지 저장, 삭제 불가(S9).
- 본인 프로젝트에 대한 동의만 처리 가능(S4).

---

### 6. 프로젝트 공개(발행)

```
POST /api/v1/projects/{projectId}/submit
```

**Auth Required**: O (판매자)

**Request**: 없음

**Response Body**

```json
{ "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f", "status": "ONGOING" }
```

**Validation / Business Rules**

- 필수 작성 항목은 `basicInfo`(사업자유형·카테고리·제목·목표금액)·`story`(소개 콘텐츠 1블록 이상)·`rewards`(미삭제 리워드 1개 이상)·`privacyConsent`(동의 이력)이다. 환불정책 특이사항은 필수값이 **아니다**.
- 위 항목이 모두 채워진 `DRAFT`만 **관리자 승인 없이 바로** `status=ONGOING`으로 전환. 미완료 시
  `422 PROJECT_NOT_SUBMITTABLE`(메시지에 누락 키 목록 포함: `basicInfo`, `story`, `rewards`, `privacyConsent`).
- 전환과 같은 트랜잭션에서 `funding_start_at`/`funding_deadline`을 확정(모금기간 기본값 30일, 코드 상수)하고
  `project.approved.v1`을 아웃박스에 적재한다(SEARCH-011).
- **판매자 알림 Kafka(`notification.raised.v1`)는 발행하지 않는다**(publisher 없음).
- 관리자 심사 단계는 **폐지됐다** — 과거 이 엔드포인트는 `PENDING_REVIEW`로만 전환하고 별도 관리자 승인
  (`POST /api/v1/admin/projects/{projectId}/review-decision`, PROJECT-030)이 필요했으나, 정책 변경으로 그
  엔드포인트와 `PENDING_REVIEW` 상태, `project_review_requests` 테이블이 모두 제거됐다.

---

### 8. 프로젝트 소개 콘텐츠 등록

```
PATCH /api/v1/projects/{projectId}/story
```

**Auth Required**: O (판매자)

**Request**: {
"title": "세상에 없는 프라이팬",
"coverImageUrl": "https://{bucket}.s3.{region}.amazonaws.com/projects/018f2c1a-.../a1b2.jpg",
"introContent": [
{ "type": "TEXT", "value": "..." },
{ "type": "IMAGE", "value": "https://{bucket}.s3.{region}.amazonaws.com/projects/018f2c1a-.../c3d4.jpg" },
{ "type": "VIDEO_URL", "value": "https://youtube.com/..." }
]
}

**Response Body**

```json
{ "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f", "updatedAt": "2026-09-05T10:00:00" }
```

**Validation / Business Rules**

- `title` 40자 제한(DB 컬럼 제약과 동일).
- `coverImageUrl`과 `introContent`의 `type=IMAGE` 항목 `value`는 반드시 #9 업로드 주소 발급 API로 발급받아 실제 업로드까지 마친 `fileUrl`이어야 한다 — 저장 시 경로(`projects/{projectId}/`로 시작)·S3 실존 여부(HeadObject)·크기(10MB 이하)를 검증하고, 하나라도 실패하면 `400 INVALID_MEDIA_URL`/`400 MEDIA_TOO_LARGE`로 거부한다. 직접 만든 URL 문자열은 저장되지 않는다.
- `type=VIDEO_URL`은 유튜브 등 외부 영상 링크 용도로, 위 S3 검증 대상이 아니다.
- `type=TEXT`의 `value`는 굵게/색상(`color`, hex)/정렬(`text-align`)/글자굵기(`font-weight`) 서식을 담은 HTML을 그대로 보낼 수 있다. 서버가 `RichTextSanitizer`로 허용 태그(`b/strong/i/em/u/p/br/span/div/ul/ol/li`)와 위 4종 CSS 선언만 남기고 나머지(스크립트, 이벤트 속성, 그 외 스타일)는 제거한 뒤 저장한다(XSS 방지, S2) — 재조회 시 정제된 HTML이 그대로 내려간다.
- 임시저장 겸용이며 부분 필드만 전달해도 저장 가능.
- 프로젝트가 이미 공개 상태이면 저장 후 `project.updated.v1`을 발행한다(#4와 동일 조건). 스토리 GET API는 별도로 두지 않는다.

---

### 9. 이미지/영상 업로드 주소 발급(S3 Presigned URL)

```
POST /api/v1/projects/{projectId}/media/upload-url
```

**Auth Required**: O (판매자)

**Request**: {
"fileName": "cover.jpg",
"contentType": "image/jpeg",
"fileSize": 2097152
}

**Response Body**

```json
{
  "uploadUrl": "https://{bucket}.s3.{region}.amazonaws.com/projects/018f2c1a-.../a1b2c3d4.jpg?X-Amz-...",
  "fileUrl": "https://{bucket}.s3.{region}.amazonaws.com/projects/018f2c1a-.../a1b2c3d4.jpg"
}
```

**Validation / Business Rules**

- 이미지/영상 URL을 프로젝트 스토리(커버이미지·소개콘텐츠, #8)·리워드(이미지, #10·#11) 등 기존 API에 저장하기 전, 이 API로 먼저 업로드 주소를 발급받아 **① S3에 직접 PUT 업로드 → ② 발급받은 `fileUrl`을 저장 API에 전달**하는 순서로 사용한다(백엔드 서버는 파일 바이트를 직접 받지 않는다).
- 소유권(S4): 요청자가 `projectId`의 `seller_id`와 일치해야 함(리워드 이미지도 이 프로젝트 네임스페이스를 사용하므로 별도 리워드 전용 엔드포인트는 두지 않음) — 불일치 시 `403 FORBIDDEN`.
- 확장자·`contentType` 화이트리스트(S5): 이미지는 `jpg`/`jpeg`/`png`/`webp`(`image/jpeg`,`image/png`,`image/webp`), 영상은 `mp4`(`video/mp4`)만 허용 — 그 외 `400 UNSUPPORTED_MEDIA_TYPE`.
- 용량 제한(S5): 이미지 10MB(10,485,760 bytes), 영상 100MB(104,857,600 bytes) 초과 시 `400 MEDIA_TOO_LARGE`.
- 저장 키는 `projects/{projectId}/{UUID}.{ext}` 형식으로 서버가 생성한다 — 클라이언트가 보낸 `fileName`은 키에 사용하지 않는다(추측 불가 파일명, S5).
- `uploadUrl`은 발급 후 5분(TTL)간만 유효, PUT 요청 시 `Content-Type` 헤더가 발급 요청의 `contentType`과 일치해야 한다(서명에 포함).
- 영상은 단일 PUT만 지원(멀티파트 업로드 미지원, 협의 완료).

---

### 10. 리워드 등록

```
POST /api/v1/projects/{projectId}/rewards
```

**Auth Required**: O (판매자)

**Request**: {
"name": "얼리버드 패키지",
"description": "...",
"imageUrl": "https://{bucket}.s3.{region}.amazonaws.com/projects/018f2c1a-.../r1.jpg",
"price": 39000,
"isLimited": true,
"quantity": 100,
"isEarlyBird": true,
"earlyBirdDiscountType": "RATE",
"earlyBirdDiscountValue": 10,
"options": [
{ "groupName": "색상", "values": ["화이트", "블랙"] }
],
"shippingFee": 3000,
"estimatedDeliveryDays": 7
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
  "hasOption": true,
  "sortOrder": 0,
  "isEarlyBird": true,
  "earlyBirdDiscountType": "RATE",
  "earlyBirdDiscountValue": 10,
  "earlyBirdDiscountedPrice": 35100,
  "shippingFee": 3000,
  "estimatedDeliveryDays": 7
}
```

**Validation / Business Rules**

- 필수값(`name`,`price`,`quantity`\[`isLimited=true`인 경우\]) 누락 → `400 INVALID_INPUT`(PRD 4.1.4).
- `isLimited=true`이면 `quantity` 필수(0 이상), `isLimited=false`이면 `quantity`는 null이어야 함(DB CHECK `chk_rewards_quantity`) — 위반 시 `400 INVALID_REWARD_QUANTITY`.
- **무제한 수량 표기**: `quantity: -1`은 "무제한"을 뜻하는 sentinel로도 허용한다 — 서버가 `isLimited:false` + `quantity:null`(canonical)로 정규화해서 저장·응답한다. 정식 계약은 여전히 `isLimited:false` + `quantity` 생략(또는 `null`)이며, `-1`은 별칭일 뿐이다. `isLimited:true`와 `quantity:-1`을 함께 보내면 모순이라 정규화하지 않고 위 규칙대로 `400 INVALID_REWARD_QUANTITY`로 거부한다.
- **배송비/예상 발송일**(둘 다 선택값, 미전달 시 `null`): `shippingFee`는 리워드별 배송비(0 이상, 0=무료배송), `estimatedDeliveryDays`는 "펀딩 종료 후 N일" 상대값(0 이상)이다. 값이 있는데 음수면 `400 INVALID_REWARD_SHIPPING_INFO`. **주의**: 배송비가 리워드별인지 프로젝트 공통인지, 예상 발송일이 상대값인지 고정 일자인지는 아직 기획 미확정이라 스키마가 바뀔 수 있다(`ProjectDomainPendingWork.md` #1 참고).
- 얼리버드 할인: `isEarlyBird=false`면 `earlyBirdDiscountType`/`earlyBirdDiscountValue`는 반드시 없어야 하고,
  `true`면 `earlyBirdDiscountType`(`AMOUNT` 정액(원) 또는 `RATE` 정률(%))과 `earlyBirdDiscountValue`가 필수다.
  `AMOUNT`는 `price`보다 작은 양수, `RATE`는 0~100 사이 정수만 허용(DB CHECK
  `chk_rewards_early_bird_discount`) — 위반 시 `400 INVALID_EARLY_BIRD_DISCOUNT`. `earlyBirdDiscountedPrice`는
  할인 적용가로, 얼리버드가 아니면 `null`이다.
- `imageUrl`은 #9로 발급받아 업로드까지 마친 `fileUrl`만 허용(경로·실존·크기 검증, 실패 시 `400 INVALID_MEDIA_URL`/`400 MEDIA_TOO_LARGE`) — 미전달 시 검증하지 않음(선택값).
- `options` 전달 시 `has_option=true`로 저장하고 `reward_option_groups`/`reward_option_values` 2단 구조로 생성.
- 생성 시 `reward.created.v1`을 아웃박스로 발행한다(ORDER-012, 파티션 키 `rewardId`). payload의 `projectId`는 외부 UUID가 아니라 **내부 Long PK**다.
- 소유권(`seller_id`) 검증(S4), `name`/`description`은 출력 인코딩 적용(S2).
- 응답에도 `simpleRefundDisabled`/`options`가 포함되지만, 생성/수정 직후 응답이라 `options`의 `groupId`/`valueId`는 `null`이다(방금 요청한 값을 그대로 반영할 뿐 DB 조회를 다시 하지 않음) — 재편집 화면처럼 실제 ID가 필요하면 #14-1로 다시 조회한다.

---

### 11. 리워드 수정

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
- `imageUrl`을 전달하는 경우 #9로 발급받은 `fileUrl`인지 등록(#10)과 동일하게 검증한다.
- 얼리버드 할인 방식/값 병합 규칙은 `quantity`와 동일하다: 명시적으로 전달되면 그 값을, `isEarlyBird=false`로
  바뀌면 `null`을, 둘 다 아니면 기존 값을 유지한다. 최종 값은 등록(#10)과 같은 정합성 규칙으로 재검증한다.
- `shippingFee`/`estimatedDeliveryDays`는 각각 전달된 필드만 갱신하고, 전달하지 않은 필드는 기존 값을 유지한다(단순 필드별 병합, `quantity`처럼 다른 필드에 연동해 자동으로 `null`이 되는 규칙은 없음).
- 수정 시 `reward.updated.v1`을 발행한다(ORDER-012). payload는 생성 이벤트와 동일 계약이며 `projectId`는 내부 Long PK. 삭제·환불정책 변경은 이 토픽을 발행하지 않는다.
- 이미 판매(주문)가 발생한 리워드의 `price` 인하/인상 등 정책은 [정책 확인 필요].

---

### 12. 리워드 삭제

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
    "description": "얼리버드 한정 패키지 구성입니다.",
    "imageUrl": "https://.../reward.png",
    "price": 39000,
    "isEarlyBird": true,
    "earlyBirdDiscountType": "RATE",
    "earlyBirdDiscountValue": 10,
    "earlyBirdDiscountedPrice": 35100,
    "isLimited": true,
    "remainingStock": 37,
    "options": [
      { "groupId": 10, "groupName": "색상", "values": [
          { "valueId": 100, "value": "화이트" },
          { "valueId": 101, "value": "블랙" } ] }
    ],
    "soldOut": false,
    "shippingFee": 3000,
    "estimatedDeliveryDays": 7
  }
]
```

**Validation / Business Rules**

- 리워드 구성·가격·옵션은 project-service가 직접 응답. `remainingStock`은 `InventoryQueryClient`(`HttpInventoryQueryClient`)로 order-service `GET /api/v1/inventories/{rewardId}`를 실시간 조회한다. `soldOut`은 `remainingStock != null && remainingStock <= 0`일 때만 `true`.
- 삭제된 리워드(`deleted_at` not null)는 응답에서 제외.
- `remainingStock=0` → `soldOut: true`로 표시, 프론트는 '알림 신청' 버튼으로 대체(PRD 13.1.4).
- order-service 조회 실패 또는 무제한 리워드는 `remainingStock: null`로 응답한다(`503 DEPENDENCY_FAILURE`를 던지지 않고 빈 값으로 degrade — best-effort 표시 데이터).
- `shippingFee`/`estimatedDeliveryDays`는 미설정이면 `null`(#10 참고, 기획 미확정으로 스키마가 바뀔 수 있음).
- `earlyBirdDiscountedPrice`는 얼리버드 할인 적용가(정액은 `price - earlyBirdDiscountValue`, 정률은
  `price - price * earlyBirdDiscountValue / 100`), 얼리버드가 아니면 `null`이다.
- **비공개(DRAFT) 프로젝트는 `404 NOT_FOUND`**(존재 여부 비노출) — 공개 여부와 무관하게 조회해야 하면 #14-1(판매자용) 사용.

---

### 14-1. 리워드 목록 조회(판매자)

```
GET /api/v1/projects/{projectId}/rewards/mine
```

**Auth Required**: O (판매자)

**Request**: Path Parameter: `projectId`

**Response Body**

```json
[
  {
    "rewardId": 1,
    "rewardDisplayCode": "R0000001",
    "name": "얼리버드 패키지",
    "description": "얼리버드 한정 패키지 구성입니다.",
    "imageUrl": "https://.../reward.png",
    "price": 39000,
    "isLimited": true,
    "quantity": 100,
    "hasOption": true,
    "sortOrder": 0,
    "isEarlyBird": true,
    "earlyBirdDiscountType": "RATE",
    "earlyBirdDiscountValue": 10,
    "earlyBirdDiscountedPrice": 35100,
    "shippingFee": 3000,
    "estimatedDeliveryDays": 7,
    "simpleRefundDisabled": false,
    "options": [
      { "groupId": 10, "groupName": "색상", "values": [{ "valueId": 100, "value": "화이트" }, { "valueId": 101, "value": "블랙" }] }
    ]
  }
]
```

**Validation / Business Rules**

- #14(소비자용)와 달리 **공개 여부와 무관하게 소유권 검증만으로 조회**한다 — DRAFT 단계의 "리워드 등록/관리"
  화면(No./리워드명/가격/수량/할인 적용여부)에서 사용. 작성 중(비공개) 프로젝트의 리워드 재편집 화면도 이 API로 조회한다.
- `options`는 #14(소비자용)와 동일하게 옵션 그룹/값의 실제 DB ID를 포함한다(재편집 시 PATCH 요청에 필요) — `hasOption=false`면 빈 배열.
- `simpleRefundDisabled`는 #13 등록값을 그대로 노출한다.
- 잔여재고/품절 여부는 포함하지 않는다(판매자 화면은 설정값만 보여주면 되고, order-service 조회가 필요 없다).
- 소유권 불일치 → `403 FORBIDDEN`, 존재하지 않는 프로젝트 → `404 NOT_FOUND`.

---

### 14-2. 리워드 상세 조회(소비자)

```
GET /api/v1/rewards/{rewardId}
```

**Auth Required**: X (공통)

**Request**: Path Parameter: `rewardId`

**Response Body**

```json
{
  "rewardId": 1,
  "rewardDisplayCode": "R0000001",
  "name": "얼리버드 패키지",
  "description": "얼리버드 한정 패키지 구성입니다.",
  "imageUrl": "https://.../reward.png",
  "price": 39000,
  "isEarlyBird": true,
  "earlyBirdDiscountType": "RATE",
  "earlyBirdDiscountValue": 10,
  "earlyBirdDiscountedPrice": 35100,
  "isLimited": true,
  "remainingStock": 37,
  "options": [
    { "groupId": 10, "groupName": "색상", "values": [
        { "valueId": 100, "value": "화이트" },
        { "valueId": 101, "value": "블랙" } ] }
  ],
  "soldOut": false,
  "shippingFee": 3000,
  "estimatedDeliveryDays": 7
}
```

**Validation / Business Rules**

- 응답 필드/계산 규칙은 #14(리워드 목록 조회)와 동일 — 리워드 상세페이지 단건 조회용.
- 리워드가 속한 프로젝트가 공개 상태(ONGOING/SUCCEEDED/FAILED)가 아니면(DRAFT, 또는 리워드가 소프트 삭제됨) `404 NOT_FOUND`.

---

### 15. 새소식 등록(판매자)

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

### 16. 새소식 목록 조회(소비자)

```
GET /api/v1/projects/{projectId}/notices
```

**Auth Required**: X (공통)

**Request**: Query: `noticeType`(선택), `sort`=`LATEST`\|`POPULAR`(기본 `LATEST`, 값은 화이트리스트만 검증)

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
- 정렬은 항상 생성일 역순(최신순)이다. `sort=POPULAR`도 400은 아니지만 **인기순을 적용하지 않는다**(조회수/인기 집계 컬럼·이벤트 없음). `LATEST`/`POPULAR` 외 값 → `400 INVALID_INPUT`.

---

### 16-1. 새소식 본문 조회

```
GET /api/v1/notices/{noticeId}
```

**Auth Required**: X (공통)

**Request**: Path Parameter: `noticeId`

**Response Body**

```json
{ "noticeId": 501, "noticeType": "PRODUCTION_UPDATE", "title": "생산 진행 상황 안내", "content": "...", "createdAt": "2026-09-05T10:00:00" }
```

**Validation / Business Rules**

- #16(목록)과 동일 — 새소식 없음 또는 소속 프로젝트 비공개는 존재 여부를 구분하지 않고 `404 NOT_FOUND`.
- 열람·재편집(판매자)용으로 본문(`content`)을 포함한다. 목록(#16)은 피드 노출용이라 본문을 담지 않는다.
- 이 API는 조회 전용이다 — 새소식 수정(PATCH)은 아직 없다(등록만 #15로 가능).

---

### 17. 새소식 댓글 등록

```
POST /api/v1/notices/{noticeId}/comments
```

**Auth Required**: O (로그인 회원)

**Request**: { "content": "기대돼요!" }

**Response Body**

```json
{ "commentId": 9001, "noticeId": 501, "content": "기대돼요!", "createdAt": "2026-09-05T10:05:00" }
```

**Validation / Business Rules**

- `content` 500자 제한(DB `VARCHAR(500)`).
- 출력 시 인코딩 적용(S2). `@LoginUser`가 있으면 작성 가능 — **구매(펀딩) 이력은 검증하지 않는다**. 비로그인 → `401 UNAUTHORIZED`.

---

### 18. 새소식 댓글 목록 조회

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

### 19. 커뮤니티 질문/응원 등록(소비자)

```
POST /api/v1/projects/{projectId}/community/posts
```

**Auth Required**: O (로그인 회원)

**Request**: { "postType": "QUESTION", "content": "배송은 언제쯤 시작되나요?" }

**Response Body**

```json
{ "postId": 7001, "postType": "QUESTION", "content": "배송은 언제쯤 시작되나요?", "createdAt": "2026-09-05T10:10:00" }
```

**Validation / Business Rules**

- `postType`은 `QUESTION`\|`CHEER`만 허용.
- 출력 인코딩 적용(S2). `@LoginUser`가 있으면 작성 가능 — **구매(펀딩) 이력은 검증하지 않는다**. 비로그인 → `401 UNAUTHORIZED`.

---

### 20. 커뮤니티 게시글 목록 조회(판매자/소비자 공용)

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
      "answer": { "content": "다음 주 중 순차 발송 예정입니다.", "updatedAt": "2026-09-05T10:20:00" },
      "createdAt": "2026-09-05T10:10:00" }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

**Validation / Business Rules**

- 동일 엔드포인트를 판매자(PROJECT-017)·소비자(PROJECT-025) 화면에서 공용으로 사용. `answeredOnly`는 호출자가 해당 프로젝트 판매자일 때만 적용되고, 아니면 조용히 무시한다(403이 아님).
- `answer`는 답변이 있으면 `{ "content", "updatedAt" }` 객체, 없으면 `null`.
- 게시글 내용 출력 시 인코딩 적용(S2).

---

### 21. 커뮤니티 답변 등록/수정

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
- 답변 등록/수정 시 **`notification.raised.v1`을 발행하지 않는다**(Kafka publisher 없음). DB UPSERT만 수행한다.
- 본인 소유 프로젝트의 게시글만 답변 가능(S4), 출력 인코딩 적용(S2).

---

### 22~24. Funding Story AI

로컬 개발/QA: [Funding Story AI 스텁 실행 안내](FundingStoryAiStub.md).

| 구간 | 공개 요청 | BE 처리 |
|---|---|---|
| 프로젝트 범위 | 모든 `/api/v1/ai/**` | `X-Project-Id`와 로그인 판매자 소유권 확인 |
| 정보 수집 | sessions · messages · SSE · confirm | 같은 경로로 AI에 전달, Core DTO는 BE가 구성 |
| 전체 생성 | `POST /api/v1/ai/runs` | 확인 시점과 최신 Core fingerprint가 다르면 `409`, `detail.action=reconfirm_summary` |
| 결과 조회 | `GET /api/v1/ai/runs/{runId}` | AI로 전달하지 않고 BE 저장 결과 반환 |
| 이미지 전달 | AI → `/internal/ai/media/upload-targets` | `projects/{projectId}/ai/` 아래 presigned PUT 발급 |
| 완료 통지 | AI → `/internal/ai/runs/{runId}/completion` | 경로·존재·크기·MIME 검증 후 결과 확정 |

**공통 헤더**

| 방향 | 인증 | 프로젝트 범위 |
|---|---|---|
| FE → BE | 사용자 인증 | `X-Project-Id: <public UUID>` |
| BE → AI | `Authorization: Bearer <service-token>` | 동일 헤더·동일 `/api/v1/ai` 경로 |
| AI → BE | `X-Internal-Api-Key` | 동일 헤더 |

**상태·저장 규칙**

- 공개 run 상태: `queued` · `running` · `succeeded` · `partially_succeeded` · `failed`.
- 전체 재생성만 지원한다. 부분/슬롯 재생성과 별도 export/apply API는 없다.
- 객체 검증 실패가 일부이면 `partially_succeeded`, 사용 가능한 결과가 없으면 `failed`로 낮춘다.
- 완료 callback을 제한 시간 안에 받지 못하면 조회 시 `failed/RUN_CALLBACK_TIMEOUT`으로 종료한다.
- 성공·부분 성공의 본문과 검증된 URL은 기존 `projects.cover_image_url`, `projects.intro_content`에 반영한다.
- `ai_funding_story_sessions`는 세션/run 식별·상태·Core fingerprint 추적에 재사용한다.
- 별도 Funding Story 테이블·자산 테이블·DB migration·ERD 변경은 없다.
- DTO 필드와 SSE/callback 형식은 DTO 계약, 호출 순서는 통합 인터페이스 명세를 따른다.

---

### 25. 프로젝트 미리보기 조회(판매자)

```
GET /api/v1/projects/{projectId}/preview
```

**Auth Required**: O (판매자)

**Request**: Path Parameter: `projectId`

**Response Body**

```json
{
  "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",
  "title": "세상에 없는 프라이팬",
  "status": "DRAFT",
  "goalAmount": 5000000,
  "coverImageUrl": "https://.../cover.png",
  "introContent": [ { "type": "TEXT", "value": "본문 텍스트" }, { "type": "IMAGE", "value": "https://.../body.png" } ],
  "fundingStatus": { "currentAmount": 0, "achievementRate": 0, "participantCount": 0, "remainingDays": null },
  "hasLiveVerification": false,
  "seller": { "sellerId": "018e9a10-....", "displayName": null },
  "categoryMajor": "패션", "categoryMinor": "의류"
}
```

공개 상세(#26)와 **동일 DTO(`ProjectDetailResponse`)** — 미공개(`DRAFT`)에서도 소유자면 조회 가능.

**Validation / Business Rules**

- 본인 소유 프로젝트만 미리보기 접근 가능, 타 판매자 → `403 FORBIDDEN`(S4).
- 응답 필드는 `projectId`/`title`/`status`/`goalAmount`/`coverImageUrl`/`introContent`/`fundingStatus`/`hasLiveVerification`/`seller`/`categoryMajor`/`categoryMinor`/`businessType`다. **rewards는 포함하지 않는다** — 리워드는 #14-1(판매자용 목록)로 별도 조회.
- 클라이언트가 화면을 조립하려면 리워드(#14-1)·환불정책(#28)·LIVE검증(#32) 등 다른 GET을 조합한다. 스토리 본문(`introContent`/`coverImageUrl`)은 이 응답에 포함되며, 쓰기는 #8 PATCH.
- `seller.displayName`은 `SellerProfileClient`가 `NoopSellerProfileClient`라 **항상 `null`**. `fundingStatus`는 `funding_status_snapshots`를 읽으며 행이 없으면 0/null.

---

### 26. 프로젝트 상세정보 조회(공개)

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
  "coverImageUrl": "https://.../cover.png",
  "introContent": [ { "type": "TEXT", "value": "본문 텍스트" }, { "type": "IMAGE", "value": "https://.../body.png" } ],
  "fundingStatus": { "currentAmount": 3200000, "achievementRate": 64, "participantCount": 128, "remainingDays": 5 },
  "hasLiveVerification": true,
  "seller": { "sellerId": "...", "displayName": null },
  "categoryMajor": "패션", "categoryMinor": "의류",
  "businessType": "GENERAL"
}
```

**Validation / Business Rules**

- `status`가 `DRAFT`인 미공개 프로젝트 조회 시 `404 NOT_FOUND`(본인이면 미리보기 API 사용).
- 응답은 `ProjectDetailResponse`만 반환한다 — rewards는 포함하지 않으며, 클라이언트는 #14(리워드)·#28(환불)·#32(LIVE검증)로 조합한다. 대표이미지(`coverImageUrl`)·소개 본문(`introContent`)은 이 응답에 포함된다(쓰기는 #8 PATCH). `businessType`은 #4 저장값을 그대로 노출해 재조회 시 기존 선택을 복원할 수 있게 한다(`GENERAL`/`SOLE`/`CORP`, 미입력이면 `null`).
- `fundingStatus`는 PROJECT-015와 같은 `funding_status_snapshots` 읽기 모델이다. Kafka 펀딩집계 컨슈머가 없어 스냅샷이 비어 있으면 금액/달성률/참여자수는 0이다.
- `hasLiveVerification`은 `live_verifications`(미삭제) 존재 여부. `seller.displayName`은 Noop 클라이언트라 `null`.
- **[2026-09-18 추가]** `categoryMajor`/`categoryMinor`는 order-service의 CATEGORY 스코프 쿠폰 매칭(ORDER-010)이 이 값을 조회해 쓴다 — 공개 계약이니 필드명을 바꾸면 그쪽 연동이 깨진다.

---

### 27. 판매자 정보/이력 조회

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

### 28. 환불정책/환불불가유형 조회

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

### 29. LIVE검증 콘텐츠 등록(판매자)

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

### 30. LIVE검증 콘텐츠 수정

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

### 31. LIVE검증 콘텐츠 삭제

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

### 32. 방송종료 후 LIVE검증 질문/답변 조회(소비자)

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

### 33. 펀딩 현황 조회(판매자)

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
  "rewardStats": [ { "rewardId": 1, "optionValueId": null, "purchasedQuantity": 30, "purchasedAmount": 1170000 } ],
  "remainingDays": 5, "lastSyncedAt": "2026-09-05T00:00:00"
}
```

**Validation / Business Rules**

- 본인 소유 프로젝트만 조회 가능(S4).
- `funding_status_snapshots` 테이블을 읽는다. `rewardStats`는 order-service가 하루 한 번 발행하는 `project.funding-reward-stats-updated.v1`로 채워진다(옵션값 단위, `optionValueId` 포함). `currentAmount`/`achievementRate`/`participantCount`는 아직 별도 집계 이벤트가 없어 행이 없으면 0, `lastSyncedAt`은 reward_stats 반영 시각이다.
- `optionValueId`가 null이면 옵션 없는 리워드 합계, 있으면 해당 옵션값 한정 통계다.
- `openNotifyCount`는 `project_open_notify_requests` COUNT, `wishCount`는 `project_wish_stats` 읽기 모델(아래 #34와 동일). `remainingDays`는 `funding_deadline` 기준 계산.

---

### 34. 찜·알림신청 건수 조회(판매자용)

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

- 본인 소유 프로젝트만 조회 가능(S4).
- 찜 카운트는 로컬 `project_wish_stats`(+멤버 가드 테이블)을 읽는다. 애플리케이션 이벤트 `ProjectWishedEvent`/`ProjectUnwishedEvent`를 `@EventListener`로 반영하는 코드는 있으나, **`project.wished.v1`/`project.unwished.v1` Kafka 컨슈머는 없다** — 브로커에서 이벤트를 받아 애플리케이션 이벤트로 변환하는 어댑터가 없어 운영 경로에서는 카운트가 갱신되지 않는다.
- `openNotifyCount`는 `project_open_notify_requests` COUNT(오픈알림 신청 API/Kafka도 이 서비스에 없음).

---

### 35. 내부 프로젝트 스냅샷 조회

```
GET /internal/projects/{projectId}
```

**Auth Required**: 내부 전용 — JWT/`@LoginUser`가 아니라 `InternalGatewaySecretFilter`가 `X-Internal-Api-Key`를 요구한다. `InternalEndpointConfig`가 `GET /internal/projects/{projectId}`를 내부 엔드포인트로 등록한다. 키 없거나 불일치 → `401 UNAUTHORIZED`.

**Request**: Path Parameter: `projectId` — **내부 Long PK** (`projects.id`). 공개 UUID가 아니다.

**호출 주체**: order/fulfillment의 **v1 Long 어댑터**와 Kafka `projectId`(Long) 해석, order `FundingProjectPublicIdBackfillRunner`. UUID 기준 소유권/제목 조회는 공개 상세 `GET /api/v1/projects/{projectId}`를 쓴다.

**Response Body**

```json
{
  "sellerId": "018e9a10-....",
  "publicId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f"
}
```

**Validation / Business Rules**

- 존재하는 프로젝트의 `sellerId`와 `publicId`만 반환한다(fulfillment 소유권 검증·알림 수신자 조회용).
- 없으면 `404 NOT_FOUND`. 게이트웨이 공개 라우트가 아니라 서비스 간 내부 호출을 전제로 한다.

---

### 36. 내부 프로젝트 배치 요약 조회

```
GET /internal/projects/summaries?ids={publicId1},{publicId2},...
```

**Auth Required**: 내부 전용 — `X-Internal-Api-Key`(`InternalEndpointConfig`에 `GET /internal/projects/summaries`로 등록). 키 없거나 불일치 → `401 UNAUTHORIZED`.

**Request**: Query Parameter: `ids` — publicId(UUID) 목록(반복 파라미터, 예: `?ids=uuid1&ids=uuid2`).

**호출 주체**: order-service 주문 목록(`GET /api/v1/orders`, V03) — 건별 단건 조회 대신 페이지 단위로 한 번만 호출해 N+1을 피한다.

**Response Body**

```json
[
  {
    "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",
    "title": "세상에 없는 프라이팬",
    "thumbnailUrl": "https://cdn.fundit.example/x.png",
    "sellerDisplayName": null
  }
]
```

**Validation / Business Rules**

- 소프트 삭제(`deleted_at`)된 프로젝트는 결과에서 빠진다(요청한 `ids`보다 응답 배열이 짧을 수 있다) — 404가 아니라 조용히 생략.
- `sellerDisplayName`은 `SellerProfileClient`로 채우는데, member-service 연동 전(`NoopSellerProfileClient`)이라 **현재는 항상 null**이다(공개 상세 `GET /api/v1/projects/{projectId}`의 `seller.displayName`과 동일한 제약).
- 목록 화면용 최소 필드만 제공한다 — 펀딩 현황·라이브 인증 여부 등은 포함하지 않는다(필요하면 공개 상세 API를 쓸 것).

---

## 이벤트 발행/구독

토픽명은 `{도메인}.{사건}.v{N}` (`event-convention.md`, `KafkaTopics`). project-service가 **발행하는** 토픽만 아래와 같다. `notification.raised.v1` publisher는 없다. `project.funding-deadline-reached.v1`도 이 서비스에서 발행하지 않는다.

| 기능 ID | 유형 | 토픽 | 조건 / payload |
| --- | --- | --- | --- |
| PROJECT-029 | 발행 | `project.approved.v1` | 필수항목 완료로 ONGOING 전환 시 아웃박스 적재. 파티션 키: 내부 `projectId`(Long). 구독: search-service(SEARCH-011) |
| PROJECT-004, PROJECT-006 | 발행 | `project.updated.v1` | 공개(`isPublic()`) 프로젝트의 기본정보/스토리 수정 시에만. DRAFT 수정은 발행하지 않음. payload 계약은 공개 전환 이벤트와 동일 |
| PROJECT-007 | 발행 | `reward.created.v1` / `reward.updated.v1` | 리워드 생성/수정 시(삭제·환불정책 PATCH는 미발행). 파티션 키: `rewardId`. `projectId`는 **내부 Long** |
| PROJECT-015 | 구독 | `project.funding-reward-stats-updated.v1` | order-service 1일 배치. `reward_stats` 전체 교체. 파티션 키: 내부 `projectId` |
| PROJECT-016 | 구독 | `project.wished.v1` / `project.unwished.v1` | 찜 통계 |
| PROJECT-018, PROJECT-029, PROJECT-030 반려 | 발행 | (없음) | `notification.raised.v1`를 발행하지 않음 |

### `project.approved.v1` / `project.updated.v1` payload

평평한 JSON(봉투 없음). `sellerDisplayName`은 `NoopSellerProfileClient`라 **현재 항상 `null`**.

```json
{
  "eventId": "project:42",
  "projectId": 1,
  "publicId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",
  "sellerId": "018e9a10-....",
  "sellerDisplayName": null,
  "title": "세상에 없는 프라이팬",
  "thumbnailUrl": "https://{bucket}.s3.{region}.amazonaws.com/projects/018f2c1a-.../a1b2.jpg",
  "categoryMajor": "테크·가전",
  "categoryMinor": "생활가전",
  "goalAmount": 5000000,
  "fundingStartAt": "2026-09-05T10:00:00Z",
  "fundingDeadline": "2026-10-05T10:00:00Z",
  "createdAt": "2026-08-20T10:00:00Z",
  "sourceVersion": 42
}
```

- `eventId`: `"project:" + 아웃박스 id`
- `sourceVersion`: 아웃박스 id(전역 단조 증가). search-service가 낮은 버전으로 최신 색인을 덮어쓰지 못하게 하는 비교 값
- `projectId`: 내부 Long PK. `thumbnailUrl`은 `coverImageUrl`을 그대로 싣는다

### `reward.created.v1` / `reward.updated.v1` payload

```json
{
  "eventId": "project:42",
  "rewardId": 1,
  "projectId": 1,
  "isLimited": true,
  "quantity": 100
}
```

- `projectId`: 내부 Long PK(공개 UUID가 아님)
- `quantity`: 무제한 리워드는 `null`

> `project.funding-deadline-reached.v1`은 `KafkaTopics`/`event-convention.md`에 있으나 **이 서비스는 발행하지 않는다.** order-service ORDER-006 리스너는 구독 중이다.

---
