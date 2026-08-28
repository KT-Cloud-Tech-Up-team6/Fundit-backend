### project 도메인 엔드포인트 목록

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| POST | /api/projects | O(판매자) | 신규 프로젝트 생성 |
| GET | /api/projects | O(판매자) | 판매자 본인 프로젝트 목록 조회 |
| GET | /api/projects/{projectId} | X | 프로젝트 상세 조회(공개 전엔 판매자 본인만) |
| PATCH | /api/projects/{projectId}/basic-info | O(판매자) | 기본정보 등록/수정 |
| PATCH | /api/projects/{projectId}/detail | O(판매자) | 상세페이지 등록/임시저장 |
| DELETE | /api/projects/{projectId} | O(판매자) | 프로젝트 삭제 |
| POST | /api/projects/{projectId}/rewards | O(판매자) | 리워드 등록 |
| PATCH | /api/projects/{projectId}/rewards/{rewardId} | O(판매자) | 리워드 수정 |
| POST | /api/projects/{projectId}/rewards/{rewardId}/options | O(판매자) | 리워드 옵션 등록 |
| GET | /api/projects/{projectId}/rewards | X | 리워드 목록 조회(옵션 포함) |
| GET | /api/projects/{projectId}/reward-info | X | 리워드 정보고시(전자상거래법) 조회 |
| POST | /api/projects/{projectId}/notices | O(판매자) | 새소식 등록 |
| GET | /api/projects/{projectId}/notices | X | 새소식 목록 조회 |
| GET | /api/projects/{projectId}/funding-summary | O(판매자) | 펀딩 현황 조회(판매자용) |
| GET | /api/projects/{projectId}/community | X | 커뮤니티(질문/응원) 목록 조회 |
| POST | /api/projects/{projectId}/community/questions | O(구매자) | 질문/응원 등록 |
| POST | /api/projects/{projectId}/community/{postId}/answers | O(판매자) | 질문 답변 등록/수정 |
| GET | /api/projects/{projectId}/reviews | X | 서포터 후기 목록 조회 |
| POST | /api/projects/{projectId}/reviews | O(구매자) | 후기 작성 |
| POST | /api/projects/{projectId}/notify | O(구매자) | 오픈알림신청 |
| DELETE | /api/projects/{projectId}/notify | O(구매자) | 오픈알림신청 취소 |

---

### 신규 프로젝트 생성

```
POST /api/projects
```

Auth Required: **O** (판매자)

Request Body: 없음 (`@LoginUser`로 주입된 `CurrentUser`를 `seller_id`로 사용)

Response Body

json

```json
{  "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",  "status": "DRAFT"}
```

Validation / Business Rules

- 로그인 사용자를 `seller_id`로 하는 `projects` 행을 `status=DRAFT`로 생성(PRD 4.1).
- `public_id`는 서버에서 UUID v7로 생성, 이후 모든 프로젝트 API는 이 값을 경로 파라미터로 사용.
- 생성 직후 클라이언트는 기본정보 등록 화면으로 이동(PRD 4.1.4).
- 생성 실패 시 오류 안내 후 재시도 가능해야 함(PRD 4.1.4).

---

### 프로젝트 목록 조회 (판매자)

```
GET /api/projects
```

Auth Required: **O** (판매자)

Query Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `status` | String | N | DRAFT / ONGOING / SUCCEEDED / FAILED, 미지정 시 전체 |
| `page` | Int | N | 기본값 0 |
| `size` | Int | N | 기본값 20 |

Response Body

json

```json
{  "content": [    {      "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",      "title": "무선 미니 가습기",      "thumbnailUrl": "https://cdn.example.com/p/1.jpg",      "status": "ONGOING",      "createdAt": "2026-07-01T09:00:00",      "fundingDeadline": "2026-08-30T23:59:59",      "dDay": 4    }  ],  "page": 0,  "size": 20,  "totalElements": 1}
```

Validation / Business Rules

- `seller_id = CurrentUser.id` 조건으로만 조회(PRD 3.1.4).
- 프로젝트 상태값(전체/준비 중/진행 중/종료)에 따라 상태별 탭 필터 제공, "종료"는 SUCCEEDED·FAILED를 묶어 반환.
- `dDay`는 `funding_deadline` 기준 서버에서 계산해 응답에 포함.
- 목록이 없는 경우 `content: []`, 클라이언트가 Empty State 처리(PRD 3.1.4).

---

### 프로젝트 상세 조회

```
GET /api/projects/{projectId}
```

Auth Required: **X** (단, 비공개(DRAFT) 프로젝트는 판매자 본인만 조회 가능)

Response Body

json

```json
{  "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",  "sellerId": 10,  "title": "무선 미니 가습기",  "categoryMajor": "홈·리빙",  "categoryMinor": "인테리어",  "goalAmount": 5000000,  "currentAmount": 3200000,  "achievementRate": 64,  "participantCount": 128,  "fundingStartAt": "2026-07-01T00:00:00",  "fundingDeadline": "2026-08-30T23:59:59",  "status": "ONGOING",  "introContent": { "text": "...", "images": ["..."], "videoUrl": null },  "refundPolicy": { "simpleRefundDisabled": false }}
```

Validation / Business Rules

- `status != DRAFT`이면 누구나 조회 가능. `DRAFT`면 `seller_id = CurrentUser.id`가 아닐 경우 404 처리(PRD 12.1).
- `currentAmount`/`achievementRate`/`participantCount`는 order-service 집계값을 조회해 병합(서비스 간 호출 또는 캐시).
- `refundPolicy.simpleRefundDisabled`는 프로젝트에 속한 리워드 중 하나라도 `simple_refund_disabled=true`이면 true로 응답(참고용 요약, 실제 판정은 리워드 단위).

---

### 프로젝트 기본정보 등록/수정

```
PATCH /api/projects/{projectId}/basic-info
```

Auth Required: **O** (판매자 본인)

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `businessType` | String | Y | GENERAL / SOLE_PROPRIETOR / CORPORATION |
| `categoryMajor` | String | Y | 대분류 |
| `categoryMinor` | String | Y | 상세분류 |
| `goalAmount` | Long | Y | 목표 금액(원) |
| `privacyAgreed` | Boolean | Y | 프로젝트 개설을 위한 개인정보 수집 동의 |

Response Body

json

```json
{  "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",  "status": "DRAFT"}
```

Validation / Business Rules

- `goalAmount >= 500000`(50만원) 아니면 400(PRD 4.2.4).
- `privacyAgreed = false`면 저장 거부, 모달 재노출(PRD 4.2.4).
- `seller_id != CurrentUser.id`면 403.
- 정상 저장 후 클라이언트는 상세페이지 작성 단계로 이동.

---

### 프로젝트 상세페이지 등록/임시저장

```
PATCH /api/projects/{projectId}/detail
```

Auth Required: **O** (판매자 본인)

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `title` | String | N | 40자 이내 |
| `thumbnailImageUrl` | String | N | 대표 이미지(사전 업로드된 URL) |
| `introContent` | Object | N | 텍스트/이미지/영상URL 조합 |
| `isDraft` | Boolean | Y | true=임시저장, false=검수 요청 |

Response Body

json

```json
{  "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",  "savedAt": "2026-07-01T10:00:00"}
```

Validation / Business Rules

- `title`은 40자 초과 시 400(PRD 5.1.4.1, `title VARCHAR(40)` 제약과 매칭).
- `isDraft=true`이면 필수값 미충족 상태로도 저장 허용(작성 중 정보 유실 방지).
- `isDraft=false`(검수 요청)이면 필수 항목(제목/대표이미지/소개/리워드 1개 이상) 누락 시 400과 함께 누락 필드 목록 반환.
- 저장 실패 시 재시도 가능해야 함(PRD 5.1.4.1).

---

### 프로젝트 삭제

```
DELETE /api/projects/{projectId}
```

Auth Required: **O** (판매자 본인)

Response Body

json

```json
{  "message": "정상 처리되었습니다."}
```

Validation / Business Rules

- `deleted_at`을 채우는 소프트 삭제로 처리.
- `status = ONGOING`(펀딩 진행 중)인 프로젝트는 삭제 불가, 409 응답.
- 삭제 전 클라이언트에서 확인 팝업 노출(PRD 3.1.4).

---

### 리워드 등록

```
POST /api/projects/{projectId}/rewards
```

Auth Required: **O** (판매자 본인)

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `name` | String | Y | 리워드명 |
| `description` | String | N | 설명 |
| `price` | Long | Y | 가격(원) |
| `isUnlimited` | Boolean | Y | 무제한 여부 |
| `isEarlyBird` | Boolean | N | 얼리버드 혜택 적용 여부, 기본 false |
| `simpleRefundDisabled` | Boolean | N | 단순변심 환불 불가 여부(각인/신선식품 등), 기본 false |
| `categoryType` | String | N | 품목 유형(COSMETICS/FOOD/ELECTRONICS 등) |
| `disclosure` | Object | N | 품목 유형별 고시 항목 값 |

Response Body

json

```json
{  "rewardId": 501,  "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f"}
```

Validation / Business Rules

- 필수값(리워드명·가격·무제한여부) 누락 시 400과 함께 안내(PRD 5.1.4.1).
- `price < 0`이면 400.
- `categoryType`이 있는데 `disclosure`가 해당 유형의 필수 항목을 채우지 못하면, 프로젝트 검수 요청(`detail.isDraft=false`) 시점에 별도로 검증(등록 시점엔 저장만 허용).
- `simpleRefundDisabled=true`로 등록 시, 해당 리워드가 포함된 `funding`은 이후 order 도메인에서 단순변심 취소(PRD 15.1)를 거부해야 함 — 이 필드가 그 판단 근거.

---

### 리워드 옵션 등록

```
POST /api/projects/{projectId}/rewards/{rewardId}/options
```

Auth Required: **O** (판매자 본인)

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `optionName` | String | Y | 예: "색상: 블랙 / 사이즈: L" |
| `sku` | String | Y | 옵션 SKU |
| `initialStock` | Int | Y | 초기 재고 수량 |

Response Body

json

```json
{  "rewardOptionId": 9001}
```

Validation / Business Rules

- `sku`는 전체 시스템에서 유일해야 함(`uq_reward_options_sku`), 중복 시 409.
- 옵션 등록과 동시에 order-service에 `inventories` 초기화 요청(이벤트 발행 또는 동기 호출)하여 `available_stock=initialStock` 세팅.
- `initialStock < 0`이면 400.

---

### 리워드 목록 조회(옵션 포함)

```
GET /api/projects/{projectId}/rewards
```

Auth Required: **X**

Response Body

json

```json
{  "rewards": [    {      "rewardId": 501,      "name": "가습기 기본형",      "price": 39000,      "isEarlyBird": true,      "isUnlimited": false,      "options": [        { "rewardOptionId": 9001, "optionName": "화이트", "availableStock": 12, "soldOut": false }      ]    }  ]}
```

Validation / Business Rules

- 재고(`availableStock`, `soldOut`)는 order-service `inventories`를 조회해 병합.
- `soldOut=true`인 옵션은 클라이언트가 [알림 신청] 버튼으로 대체 노출(PRD 13.1.4).
- 삭제된 리워드/옵션은 응답에서 제외.

---

### 리워드 정보고시 조회

```
GET /api/projects/{projectId}/reward-info
```

Auth Required: **X**

Response Body

json

```json
{  "rewards": [    {      "rewardId": 501,      "name": "가습기 기본형",      "categoryType": "ELECTRONICS",      "disclosure": { "모델명": "H-100", "정격전압": "220V", "제조자": "..." }    }  ]}
```

Validation / Business Rules

- 프로젝트 내 리워드 수만큼 고시 블록을 반복 노출(PRD 12.7.4).
- `disclosure` 값이 없는 항목은 `"정보 없음"`으로 클라이언트가 표시(서버는 null 그대로 반환).

---

### 새소식 등록

```
POST /api/projects/{projectId}/notices
```

Auth Required: **O** (판매자 본인)

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `noticeType` | String | Y | 리워드안내/이벤트/제작과정/발송정보/달성률/교환환불/결제안내/FAQ |
| `title` | String | Y | 제목 |
| `content` | String | Y | 본문 |

Response Body

json

```json
{  "noticeId": 3001}
```

Validation / Business Rules

- `noticeType`은 사전 정의된 값만 허용, 그 외 400.

---

### 새소식 목록 조회

```
GET /api/projects/{projectId}/notices
```

Auth Required: **X**

Query Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `noticeType` | String | N | 필터. 미지정 시 전체 |
| `sort` | String | N | LATEST(기본) / POPULAR |

Response Body

json

```json
{  "content": [    { "noticeId": 3001, "noticeType": "발송정보", "title": "1차 발송 안내", "createdAt": "2026-07-10T09:00:00" }  ]}
```

Validation / Business Rules

- 기본 정렬은 최신순(PRD 5.1.4.1).

---

### 펀딩 현황 조회(판매자)

```
GET /api/projects/{projectId}/funding-summary
```

Auth Required: **O** (판매자 본인)

Response Body

json

```json
{  "currentAmount": 3200000,  "goalAmount": 5000000,  "achievementRate": 64,  "participantCount": 128,  "wishCount": 340,  "openNotifyCount": 52,  "dDay": 4,  "rewardSales": [    { "rewardOptionId": 9001, "optionName": "화이트", "soldQuantity": 40 }  ]}
```

Validation / Business Rules

- 데이터 갱신 주기 1일(24시간) 기준(PRD 7.1.3) — 실시간 집계가 아니라 배치/캐시 값 반환 가능.
- `wishCount`는 member-service `wishes`, `openNotifyCount`는 `project_open_notify_requests` 집계.
- `rewardSales`는 order-service `funding_line_items` 집계.

---

### 커뮤니티 목록 조회

```
GET /api/projects/{projectId}/community
```

Auth Required: **X**

Query Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postType` | String | N | QUESTION / CHEER, 미지정 시 전체 |
| `answered` | Boolean | N | true=답변완료만, false=미답변만 |

Response Body

json

```json
{  "content": [    {      "postId": 7001,      "postType": "QUESTION",      "content": "발송은 언제부터 시작하나요?",      "memberNickname": "구매자1234",      "createdAt": "2026-07-05T12:00:00",      "answer": null    }  ]}
```

Validation / Business Rules

- `answered=false`로 미답변 질문만 필터링해 판매자가 놓치지 않도록 지원(PRD 7.2.2).

---

### 질문/응원 등록

```
POST /api/projects/{projectId}/community/questions
```

Auth Required: **O** (구매자)

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `postType` | String | Y | QUESTION / CHEER |
| `content` | String | Y | 본문 |

Response Body

json

```json
{  "postId": 7001}
```

Validation / Business Rules

- 로그인 사용자만 작성 가능.

---

### 질문 답변 등록/수정

```
POST /api/projects/{projectId}/community/{postId}/answers
```

Auth Required: **O** (판매자 본인)

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `content` | String | Y | 답변 내용 |

Response Body

json

```json
{  "message": "정상 처리되었습니다."}
```

Validation / Business Rules

- 질문당 답변은 1개(`uq_community_answers_post`) — 이미 존재하면 UPDATE로 처리(신규 생성 아님).
- 답변 등록/수정 시 작성자에게 온사이트 알림·보조 채널로 통지(PRD 7.2.3, notification-service 연동).
- `seller_id != CurrentUser.id`면 403.

---

### 서포터 후기 목록 조회

```
GET /api/projects/{projectId}/reviews
```

Auth Required: **X**

Response Body

json

```json
{  "content": [    { "reviewId": 4001, "memberNickname": "구매자1234", "content": "배송이 빨랐어요", "createdAt": "2026-08-01T09:00:00" }  ]}
```

---

### 후기 작성

```
POST /api/projects/{projectId}/reviews
```

Auth Required: **O** (구매자)

Request Body

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `fundingId` | Long | Y | 후기 대상 펀딩 참여 건 |
| `content` | String | Y | 후기 내용 |

Response Body

json

```json
{  "reviewId": 4001}
```

Validation / Business Rules

- ⚠️ "배송완료 건만 후기 작성 가능"은 DB로 강제할 수 없으므로, order-service에 `fundingId`의 배송 상태를 동기 조회하거나 order-service가 발행한 배송완료 이벤트를 구독해 검증(ERD comment 그대로 반영).
- 본인 소유가 아닌 `fundingId`로 요청 시 403.

---

### 오픈알림신청 / 취소

```
POST /api/projects/{projectId}/notify
DELETE /api/projects/{projectId}/notify
```

Auth Required: **O** (구매자)

Request Body: 없음

Response Body

json

```json
{  "message": "정상 처리되었습니다."}
```

Validation / Business Rules

- `(project_id, member_id)` 유니크 — 중복 신청 시 idempotent하게 200 처리.
- 프로젝트 오픈(상태가 ONGOING으로 전환) 시 신청자 전원에게 알림 발송(notification-service 연동, PRD 3.1.4/7.1.4).