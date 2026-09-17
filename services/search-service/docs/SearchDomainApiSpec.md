# Search 도메인 API 명세서

> `SearchDomainFunctionalSpec.md`(SEARCH-001~015)의 REST 계약입니다. 공통 규칙은 `api-convention.md`/`error-handling.md`를 따릅니다.
> **이 문서의 모든 조회 엔드포인트는 `SearchDomainFunctionalSpec.md` SEARCH-011(프로젝트 색인 이벤트)이 구현되기 전까지는 형식상 정상 동작하되 항상 빈 결과만 반환합니다.** 선행 조건은 `SearchERD.md` 5-①을 참고하세요.
>
> 경로 프리픽스는 project-service가 이미 쓰고 있는 `/api/v1/projects`와 겹치지 않도록 `/api/v1/home`, `/api/v1/categories`, `/api/v1/search` 하위로 분리했습니다(게이트웨이 라우팅 확인 필요 — 아래 "남은 확인 필요 사항" 참고).

## 엔드포인트 목록

| # | Method | Path | 설명 | 인증 | 관련 기능 ID |
| --- | --- | --- | --- | --- | --- |
| 1 | GET | `/api/v1/home/feed` | 홈 피드 추천 프로젝트 조회 | X (공통) | SEARCH-001 |
| 2 | GET | `/api/v1/home/lives` | 홈 진행 중 LIVE 배너 조회 | X (공통) | SEARCH-002 |
| 3 | GET | `/api/v1/categories` | 카테고리 대/중분류 트리 조회 | X (공통) | SEARCH-003 |
| 4 | GET | `/api/v1/categories/{categoryMajor}/projects` | 카테고리별 프로젝트 목록 조회 | X (공통) | SEARCH-004 |
| 5 | GET | `/api/v1/search/projects` | 통합 검색 — 상품 탭 | X (공통) | SEARCH-005, SEARCH-008 |
| 6 | GET | `/api/v1/search/lives` | 통합 검색 — LIVE 탭 | X (공통) | SEARCH-006, SEARCH-008 |
| 7 | GET | `/api/v1/search/sellers` | 통합 검색 — 판매자 탭 | X (공통) | SEARCH-007, SEARCH-008 |
| 8 | GET | `/api/v1/search/recent-keywords` | 내 최근 검색어 목록 조회 | O (구매자) | SEARCH-009 |
| 9 | DELETE | `/api/v1/search/recent-keywords/{keyword}` | 최근 검색어 개별 삭제 | O (구매자) | SEARCH-009 |
| 10 | DELETE | `/api/v1/search/recent-keywords` | 최근 검색어 전체 삭제 | O (구매자) | SEARCH-009 |
| 11 | GET | `/api/v1/search/popular-keywords` | 인기 검색어 조회 | X (공통) | SEARCH-010 |

> SEARCH-008(최근 검색어 자동 저장)은 별도 엔드포인트가 아니라 5·6·7번(검색 실행) API 내부에서 로그인 회원에 한해 부수 효과로 처리됩니다. SEARCH-011~015(색인 동기화·집계 배치)는 Kafka 컨슈머·스케줄러로만 동작해 REST 엔드포인트가 없습니다 — 하단 "이벤트 발행/구독" 섹션 참고.

---

## 상세 명세

### 1. 홈 피드 추천 프로젝트 조회

```
GET /api/v1/home/feed
```

**Auth Required**: X (공통, 로그인 시 개인화 판단에 `X-User-Id` 활용 가능)

**Request**: Query Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `size` | Int | N | 노출 개수(기본 20) |

**Response Body**

```json
{
  "content": [
    {
      "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",
      "projectDisplayCode": "F0000123",
      "title": "세상에 없는 프라이팬",
      "thumbnailUrl": "https://cdn.example.com/p/123/thumb.jpg",
      "categoryMajor": "테크·가전",
      "categoryMinor": "생활가전",
      "status": "ONGOING",
      "achievementRate": 64,
      "remainingDays": 5,
      "sellerDisplayName": "프라이팬장인"
    }
  ]
}
```

**Validation / Business Rules**

- 비페이지네이션 단일 목록(무한스크롤이 아닌 "영역" 성격 — PRD 10.1.4). 개인화 미동의/비로그인 회원 기준 인기·신규 정렬만 이번 범위에 포함(`SearchDomainFunctionalSpec.md` SEARCH-001 검토의견 참고).
- `achievementRate`/`remainingDays`는 `project_documents.funding_stats_synced_at` 기준 스냅샷이며 실시간이 아니다(SEARCH-013 동기화 주기에 종속).
- 색인이 비어 있으면(콜드 스타트, 또는 SEARCH-011 미구현 상태) `content: []` 반환 — 에러 아님.

---

### 2. 홈 진행 중 LIVE 배너 조회

```
GET /api/v1/home/lives
```

**Auth Required**: X (공통)

**Request**: 없음

**Response Body**

```json
{ "content": [] }
```

**Validation / Business Rules**

- **현재는 항상 `content: []`을 반환하는 스텁이다.** live-service 미착수로 `live_documents`를 채울 수 없다(`SearchDomainFunctionalSpec.md` SEARCH-002 참고). 프론트는 빈 배열을 "LIVE 없음"으로 처리해 영역을 자연스럽게 숨기면 되므로 API 계약 자체는 지금 확정해도 무방하다.
- live-service 착수 후 실제 데이터가 채워지면 응답 스키마는 `{ liveId, projectId, title, thumbnailUrl, viewerCount }[]` 형태가 될 예정이다.

---

### 3. 카테고리 대/중분류 트리 조회

```
GET /api/v1/categories
```

**Auth Required**: X (공통)

**Request**: 없음

**Response Body**

```json
{
  "categories": [
    {
      "categoryMajor": "테크·가전",
      "categoryMinors": [
        { "categoryMinor": "생활가전", "displayOrder": 1 },
        { "categoryMinor": "모바일·태블릿", "displayOrder": 2 }
      ]
    }
  ]
}
```

**Validation / Business Rules**

- `categories.display_order` 오름차순, 대분류 등록 순서 유지.
- 이 응답의 대분류/중분류 표기는 project-service `categories`와 항상 같아야 한다(`SearchERD.md` 5-④, 전체 체계 미확정 상태 — 현재는 최소 시드 기준).

---

### 4. 카테고리별 프로젝트 목록 조회

```
GET /api/v1/categories/{categoryMajor}/projects
```

**Auth Required**: X (공통)

**Request**

- Path Parameter: `categoryMajor`
- Query Parameter:

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `categoryMinor` | String | N | 미지정 시 대분류 전체 |
| `sort` | String | N | `POPULAR`\|`RECENT`\|`DEADLINE` (기본 `POPULAR`) |
| `page`, `size` | Int | N | 기본 0/20 |

**Response Body**

```json
{
  "content": [ { "...": "1번 엔드포인트와 동일한 프로젝트 카드 구조" } ],
  "page": 0, "size": 20, "totalElements": 5, "totalPages": 1, "hasNext": false
}
```

**Validation / Business Rules**

- 존재하지 않는 `categoryMajor`/`categoryMinor` 조합 → `SearchErrorCode.INVALID_CATEGORY`(400).
- `status='ONGOING'`만 대상(진행중만 카테고리 탐색 대상으로 가정 — PRD 10.2가 종료 프로젝트 포함 여부를 명시하지 않음)[가정, 확인 필요].
- 결과 0건 → `content: []`(프론트가 "해당 조건에 맞는 프로젝트가 없습니다" 표시, PRD 10.2.4).

---

### 5. 통합 검색 — 상품 탭

```
GET /api/v1/search/projects
```

**Auth Required**: X (공통, 로그인 시 최근 검색어 자동 저장)

**Request**: Query Parameter

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `keyword` | String | Y | 1자 이상 |
| `subTab` | String | N | `ONGOING`\|`ENDED` (기본 `ONGOING`) |
| `sort` | String | N | `POPULAR`\|`RECENT`\|`DEADLINE` (기본 `POPULAR`, `ENDED` 탭에서는 `DEADLINE` 무의미) |
| `page`, `size` | Int | N | 기본 0/20 |

**Response Body**

```json
{
  "content": [ { "...": "1번 엔드포인트와 동일한 프로젝트 카드 구조" } ],
  "page": 0, "size": 20, "totalElements": 3, "totalPages": 1, "hasNext": false
}
```

**Validation / Business Rules**

- `keyword` 공백/누락 → `CommonErrorCode.INVALID_INPUT`(400).
- 검색 실행 시 `search_query_logs`에 `(memberId 또는 null, keyword, resultCount)` 기록(SEARCH-010 소스), **응답을 지연시키지 않도록 비동기 처리 권장**[구현 메모].
- `X-User-Id` 헤더가 있으면(로그인) 같은 트랜잭션 경계와 무관하게 `recent_search_keywords`에 upsert(SEARCH-008) — 이 저장이 실패해도 검색 응답 자체는 정상 반환(부가 기능 실패가 주 기능을 막지 않음).
- 결과 0건 → `content: []`("검색 결과가 없습니다" 안내는 프론트 처리).

---

### 6. 통합 검색 — LIVE 탭

```
GET /api/v1/search/lives
```

**Auth Required**: X (공통)

**Request**: Query Parameter — `keyword`(Y), `subTab`(`LIVE`\|`SCHEDULED`, 기본 `LIVE`), `page`, `size`

**Response Body**

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0, "hasNext": false }
```

**Validation / Business Rules**

- 2번 엔드포인트와 동일하게 **현재는 항상 빈 결과를 반환하는 스텁**(live-service 미착수).
- `keyword` 검증·로그 적재 로직은 5번과 동일하게 적용(검색 로그는 탭과 무관하게 전부 `search_query_logs`에 쌓여 인기 검색어 집계에 반영).

---

### 7. 통합 검색 — 판매자 탭

```
GET /api/v1/search/sellers
```

**Auth Required**: X (공통)

**Request**: Query Parameter — `keyword`(Y), `page`, `size`

**Response Body**

```json
{
  "content": [
    { "sellerId": "018f...", "sellerDisplayName": "프라이팬장인", "ongoingProjectCount": 1, "totalProjectCount": 4 }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

**Validation / Business Rules**

- `seller_summary` 뷰 기준 조회, 사업자 개인정보(연락처 등)는 응답에 포함하지 않음(S9).
- 판매자 상세는 이 응답에 없다 — 클라이언트는 `sellerId`로 project-service `GET /api/v1/sellers/{sellerId}`(PROJECT-021)를 호출해 상세를 가져온다[가정].

---

### 8. 내 최근 검색어 목록 조회

```
GET /api/v1/search/recent-keywords
```

**Auth Required**: O (구매자)

**Request**: Query Parameter — `size`(N, 기본 10)

**Response Body**

```json
{
  "content": [
    { "keyword": "무선 이어폰", "searchedAt": "2026-09-16T21:00:00" }
  ]
}
```

**Validation / Business Rules**

- 본인 최근 검색어만 조회(S4).
- `searched_at` 내림차순.

---

### 9. 최근 검색어 개별 삭제

```
DELETE /api/v1/search/recent-keywords/{keyword}
```

**Auth Required**: O (구매자)

**Request**: Path Parameter: `keyword`

**Response Body**: 204 No Content

**Validation / Business Rules**

- **Idempotent.** 존재하지 않는 키워드 삭제 요청도 204(찜 해제와 동일 원칙, member-service MEMBER-005 참고).
- 본인 소유 행만 삭제(S4) — `member_id`는 항상 `@LoginUser`에서 주입.

---

### 10. 최근 검색어 전체 삭제

```
DELETE /api/v1/search/recent-keywords
```

**Auth Required**: O (구매자)

**Request**: 없음

**Response Body**: 204 No Content

**Validation / Business Rules**

- 본인 소유 행 전체 삭제. 저장된 검색어가 없어도 204(idempotent).

---

### 11. 인기 검색어 조회

```
GET /api/v1/search/popular-keywords
```

**Auth Required**: X (공통)

**Request**: 없음

**Response Body**

```json
{
  "content": [
    { "rank": 1, "keyword": "무선 이어폰" },
    { "rank": 2, "keyword": "캠핑 의자" }
  ]
}
```

**Validation / Business Rules**

- `popular_search_keywords`를 `rank` 오름차순 그대로 반환(정렬·집계는 SEARCH-015 배치가 이미 끝낸 상태).
- 집계 배치가 한 번도 돌지 않았으면 `content: []`.

---

## 에러 코드 매핑

`SearchDomainFunctionalSpec.md`의 표와 동일합니다(중복 관리 방지를 위해 여기서는 요약만 둡니다). 신규 도메인 코드는 `SearchErrorCode implements ErrorCode` 하나로 시작하며, 현재 확정된 신규 코드는 다음과 같습니다.

| 코드 | HTTP | 설명 | 관련 엔드포인트 |
| --- | --- | --- | --- |
| `SearchErrorCode.INVALID_CATEGORY`(신규) | 400 | 존재하지 않는 카테고리 대/중분류 조합 | #4 |

그 외 입력값 오류(`INVALID_INPUT`), 미인증(`UNAUTHORIZED`), 결과 없음(에러 아님, 빈 배열) 등은 전부 기존 `CommonErrorCode`를 그대로 사용합니다.

---

## 이벤트 발행/구독

REST로 노출되지 않는 이벤트/배치 기반 기능(SEARCH-011~015)은 아래와 같이 연결됩니다. search-service는 발행하는 이벤트가 없습니다(순수 구독자).

| 기능 ID | 유형 | 이벤트/트리거 | 방향 |
| --- | --- | --- | --- |
| SEARCH-011 | 이벤트 구독 | `project.approved.v1`/`project.updated.v1`(**신설 필요** — project-service가 아직 발행하지 않음) → `project_documents` upsert | project-service → search-service 구독 |
| SEARCH-012 | 이벤트 구독 | `funding.succeeded.v1`/`funding.goal-failed.v1`(기존) → `status` 전이 | order-service → search-service 구독(신규 컨슈머 그룹) |
| SEARCH-013 | 이벤트 구독 | 펀딩 집계 이벤트(**미확정** — project-service PROJECT-015와 공동 이슈) → 펀딩 통계 동기화 | order-service → search-service 구독(신규 컨슈머 그룹) |
| SEARCH-014 | 이벤트 구독 | `project.wished.v1`/`project.unwished.v1`(기존) → `wish_count` 증감 | member-service → search-service 구독(신규 컨슈머 그룹) |
| SEARCH-015 | 스케줄러 | 인기 검색어 집계(Kafka 아님, 내부 배치) | - |

> 상세 반영 이력·미해결 사항은 `SearchDomainFunctionalSpec.md` 하단 "⚠️ 남은 확인 필요 사항"과 `SearchERD.md` 5번을 참고하세요.

## ⚠️ 남은 확인 필요 사항

- **게이트웨이 라우팅**: `/api/v1/home/*`, `/api/v1/categories/*`, `/api/v1/search/*`를 search-service로 라우팅하도록 게이트웨이 설정에 추가해야 한다. `/api/v1/categories/{categoryMajor}/projects`가 project-service의 `/api/v1/projects/**` 패턴과 경로상 겹치지 않는지 게이트웨이 라우팅 규칙(Spring Cloud Gateway)을 실제로 확인 필요.
- **`config-convention.md`/`settings.gradle`에 search-service 슬롯 추가** — `SearchERD.md` 5-⑨와 동일(DB 포트 5440, 앱 포트 8089 제안).
- SEARCH-011/SEARCH-013이 막혀 있는 동안에는 #1~#7 엔드포인트 전부가 정상 200 응답에 빈 결과만 내려준다는 점을 프론트/QA에 사전 공유 필요.
