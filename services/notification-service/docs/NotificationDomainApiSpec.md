## 알림(Notification) 도메인

> 이 문서는 설계 의도와 비즈니스 규칙을 담습니다. 구현 착수 후에는 실행 중인 서비스가 제공하는 OpenAPI 문서(`GET /api/v1/notifications/api-docs.yaml`)가 구현 현황의 기준이 됩니다.
> 기능명세서(NOTI-001~007)와 **미확정/보류 사항 목록**은 `NotificationFunctionalSpec.md`를 참고하세요.
>
> `Auth Required: O` 엔드포인트는 게이트웨이(`platform:gateway-service`)가 JWT를 검증해 주입한 `X-User-Id` 헤더로 사용자를 식별합니다.
> 서비스는 이 헤더를 직접 파싱하지 않고 `@LoginUser CurrentUser`로 주입받습니다. 게이트웨이를 우회한 직접 호출은 `X-Internal-Api-Key`가 없어 401로 차단됩니다.
>
> **MVP 채널은 온사이트 알림함 하나이고, 알림 생성은 전부 Kafka 컨슈머를 거칩니다**(2026-09-11 확정) — 알림을 만드는 REST 엔드포인트는 없습니다.
> 사내 초안 대비 변경한 지점은 각 엔드포인트의 "검토의견(변경사항)"에 표시했습니다. 스키마 DDL은 같은 폴더의 `notification-schema.sql`이 정본입니다.

### 알림 도메인 엔드포인트 목록

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/notifications` | O (구매자) | 알림 목록 조회 (NOTI-003) |
| PATCH | `/api/v1/notifications/{notificationId}/read` | O (구매자) | 알림 읽음 처리 (NOTI-005) |
| GET | `/api/v1/notifications/unread-count` | O (구매자) | 안읽음 개수 조회 (NOTI-007) |
| GET | `/api/v1/notification-settings` | O | 알림 유형별 현재 수신 설정 조회 (NOTI-004) |
| PUT | `/api/v1/notification-settings` | O (구매자) | 알림 유형별 수신 설정 변경 (NOTI-004) |
| PUT | `/api/v1/lives/{liveId}/notify` | O (구매자) | LIVE 시작 알림 신청 (idempotent, NOTI-002) |
| DELETE | `/api/v1/lives/{liveId}/notify` | O (구매자) | LIVE 시작 알림 해제 (idempotent, NOTI-002) |

#### 참고 — API가 없는 처리

| ID | 트리거 방식 | 설명 |
| --- | --- | --- |
| NOTI-006 | Kafka 이벤트 구독 | **알림 생성의 유일한 입구.** 수신자가 채워진 이벤트를 받아 알림함에 적재 |
| NOTI-001 | Kafka 이벤트 구독(프로젝트 오픈) | 오픈예정 프로젝트 찜 알림 발송. member-service가 `wishes`를 훑어 이벤트로 전달하는 방향 권고(미확정) |

---

### 알림 목록 조회 (NOTI-003)

```
GET /api/v1/notifications
```

Auth Required: **O** (구매자)

Query Params

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `page` | Integer | N | 0부터 시작, 기본 0 |
| `size` | Integer | N | 기본 20 |

Response Body

```json
{
  "content": [
    {
      "notificationId": 9001,
      "notifType": "LIVE_START",
      "title": "팔로우한 브랜드가 LIVE를 시작했어요",
      "relatedUrl": "/live/0199...",
      "readAt": "2026-09-03T10:18:00+09:00",
      "createdAt": "2026-09-03T10:15:00+09:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 134,
  "totalPages": 7,
  "hasNext": true
}
```

Validation / Business Rules

- 본인 알림만 조회 가능. 조회 조건의 회원 ID는 `@LoginUser`에서만 가져오고, 요청 파라미터로 받지 않는다.
- 최신순(`created_at` 내림차순) 정렬.
- `notifType`은 아래 "알림 유형" 표의 값이며 `notifications.notif_type`과 동일 체계다.
- `readAt`이 `null`이면 안 읽은 알림. **항목별** 읽음 표시용이며, 알림함 아이콘의 **전체 안읽음 개수는 이 목록으로 구할 수 없다**(페이징되므로 해당 페이지 안에서만 셀 수 있음) — 전용 API(NOTI-007)를 쓴다.
- 안 읽은 알림은 **이 키가 응답에서 아예 빠진다**(전 서비스 공통 `non_null` 설정). 위 예시는 읽은 알림이다 — 읽음 여부는 `if (!n.readAt)`처럼 falsy로 판단하고 `=== null`로 비교하지 않는다.
- **검토의견(변경사항)**: 초안 응답은 `{ content, hasNext }`였으나 `.claude/rules/api-convention.md`의 `PageResponse<T>` 규약(`content`/`page`/`size`/`totalElements`/`totalPages`/`hasNext`)에 맞췄다. 다른 서비스의 목록 조회와 형태를 통일한다.

---

### 알림 읽음 처리 (NOTI-005)

```
PATCH /api/v1/notifications/{notificationId}/read
```

Auth Required: **O** (구매자)

Request Body: 없음

Response Body

```json
{ "notificationId": 9001, "readAt": "2026-09-03T10:20:00+09:00" }
```

Validation / Business Rules

- 본인 알림만 처리 가능.
- **idempotent** — 이미 읽은 알림에 다시 호출하면 기존 `readAt`을 그대로 반환한다(덮어쓰지 않음, 실패로 처리하지 않음).
- 존재하지 않는 알림이거나 타인의 알림이면 **404** — 403이 아니라 404를 쓰는 이유는 알림 ID를 넣어보며 타인 알림의 존재 여부를 캐낼 수 없게 하기 위함이다.

---

### 안읽음 개수 조회 (NOTI-007)

```
GET /api/v1/notifications/unread-count
```

Auth Required: **O** (구매자)

Request Body: 없음

Response Body

```json
{ "unreadCount": 7 }
```

Validation / Business Rules

- 본인의 `read_at IS NULL` 알림 건수만 센다.
- 알림함 아이콘 뱃지용이며 **알림함 밖(홈 등)에서도 호출**되므로 NOTI-003 목록 응답에 얹지 않고 별도 엔드포인트로 둔다.
- `(member_id) WHERE read_at IS NULL` partial 인덱스로 처리한다 — 전체 행이 아니라 안읽음만 인덱싱해 알림이 쌓여도 크기가 늘지 않는다.
- 조회 실패는 뱃지 미표시로 처리하고 알림함 진입 자체를 막지 않는다.

---

### 알림 수신설정 조회 (NOTI-004)

```
GET /api/v1/notification-settings
```

Auth Required: **O**

Response Body

```json
[
  { "notifType": "LIVE_START", "enabled": true },
  { "notifType": "SHIPPING_UPDATE", "enabled": false }
]
```

Validation / Business Rules

- 본인 설정만 조회한다. 재접속 시 설정 화면 복원용.
- **알림 유형 전체**를 아래 "알림 유형" 표 순서로 돌려준다. 수신 거부 행이 없는 유형은 기본값인 `enabled=true`로 채운다 — 신규 회원도 초기 데이터 없이 전체 목록이 나온다.
- 판매자 전용 유형(`SELLER_UPDATE_DUE`)도 포함한다(`PUT`이 모든 유형을 받으므로 맞춘다). 화면별 노출 여부는 FE가 거른다.

---

### 알림 수신설정 변경 (NOTI-004)

```
PUT /api/v1/notification-settings
```

Auth Required: **O** (구매자)

Request Body

| 이름 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `notifType` | String | Y | 아래 "알림 유형" 표의 값 |
| `enabled` | Boolean | Y | `false`면 해당 유형 수신 거부 |

```json
{ "notifType": "SHIPPING_UPDATE", "enabled": false }
```

Response Body

```json
{ "saved": true }
```

Validation / Business Rules

- 본인 설정만 변경 가능.
- 저장 구조는 **`notification_settings(member_id, notif_type)` 행이 존재하면 수신 거부**다. `enabled=false`면 insert(이미 있으면 무시), `true`면 delete(없으면 무시). 둘 다 idempotent.
- **기본값은 "행 없음 = 수신"** — 신규 회원은 별도 초기 데이터 없이 모든 알림을 받는다.
- 정의되지 않은 `notifType`은 400.
- **검토의견(변경사항)**: 초안은 `channel`(`ONSITE`/`PUSH`/`EMAIL`/`SMS`)을 포함한 `(member_id, notif_type, channel)` 3키 upsert에 `enabled` 컬럼을 두는 구조였다. MVP 채널이 온사이트 하나뿐이라 `channel`에는 단일값만 들어가고 나머지 enum 값은 쓰이지 않는 분기가 된다. `channel` 축과 `enabled` 컬럼을 제거했고, **채널이 늘어나는 시점에 `channel` 컬럼을 추가하면 된다**. 요청/응답 형태는 초안과 동일해 API 계약은 바뀌지 않는다.

---

### LIVE 알림 신청/해제 (NOTI-002)

```
PUT /api/v1/lives/{liveId}/notify
DELETE /api/v1/lives/{liveId}/notify
```

Auth Required: **O** (구매자)

Request Body: 없음

Response Body

```json
{ "notifying": true }    // PUT  — 신청됨
{ "notifying": false }   // DELETE — 해제됨
```

Validation / Business Rules

- 본인 알림 신청만 처리 가능. 회원 식별자는 요청 본문으로 받지 않는다.
- **idempotent** — 이미 신청된 상태의 `PUT`, 이미 해제된 상태의 `DELETE` 모두 같은 결과를 반환한다(중복 요청·네트워크 재시도를 실패로 처리하지 않음). 찜 등록/해제(`MEMBER-005`)와 동일한 원칙.
- LIVE 시작 시 이 신청 목록이 `LIVE_START` 알림의 수신자가 된다.

> **⚠️ 미결 — 신청 데이터 소유 서비스**
>
> ERD상 `streaming.live_notify_requests`는 live-service DB에 있으나, **live-service는 아직 개발 착수 전이라 코드가 없다**(`settings.gradle` 서비스 배열에 미포함). 방송 운영 여부가 아니라 서비스가 아직 만들어지지 않았다는 뜻이다.
> **notification-service가 소유하는 쪽을 권고**한다 — 알림을 실제로 발송하는 주체가 notification-service이고, live-service는 "LIVE가 시작됐다"만 던지면 된다.
> live-service가 소유하면 발송 시점마다 조회 홉이 하나 늘고, 서비스 간 동기 호출이 추가된다.
> 이 문서에 ERD 확정 권한이 없으므로 **live-service 착수 시점에 확정 필요**하다.

---

## 스키마 (MVP 범위)

DDL 정본은 같은 폴더의 **`notification-schema.sql`** 이다(초안 대비 변경 내역도 그 파일 하단 주석에 있음). 아래는 요약과 설계 근거다.

> **두 테이블 모두 "단순 애그리거트"다.** `.claude/rules/persistence-convention.md`의 복잡도 판단 표가
> `NotificationSetting`을 단순 애그리거트 예시로 이미 등재해뒀고, `Notification`도 값 저장·조회와
> `read_at` 1회 기록이 전부라 같은 분류다. 따라서 **`domain` 패키지·Mapper·PersistenceAdapter·포트 인터페이스를
> 만들지 않는다** — `infrastructure/persistence/{aggregate}/`에 `JpaEntity` + `JpaRepository`만 두고
> `application`이 직접 주입받는다(규약 2번 방식).

### `notifications`

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `id` | BIGINT IDENTITY PK | |
| `event_id` | VARCHAR(64) NOT NULL | 발행 측이 싣는 이벤트 고유 ID. 중복 소비 차단용 |
| `member_id` | UUID NOT NULL | 수신자. **FK 아님** — member-service 참조 |
| `notif_type` | VARCHAR(30) NOT NULL | 아래 "알림 유형"의 값. **DB CHECK 제약은 두지 않는다** — 레포가 열거값에 CHECK를 쓰지 않는 관행(`accounts.role`, `reward_event_outbox.event_type`)을 따르고, 값 검증은 앱이 한다 |
| `title` | VARCHAR(100) NOT NULL | 알림함 노출 문구. 프로젝트 제목이 40자 제한이라 문구에 끼워도 여유가 있다 |
| `related_url` | TEXT NOT NULL | 알림 선택 시 이동 경로. 현재 8종 전부 목적지가 있어 NOT NULL |
| `read_at` | TIMESTAMPTZ NULL | `NULL`이면 안 읽음. 최초 1회만 기록 |
| `created_at` | TIMESTAMPTZ NOT NULL | |

- `idx_notifications_member_created (member_id, created_at DESC)` — 유일한 읽기 경로가 NOTI-003의 최신순 페이징이다.
- `idx_notifications_unread (member_id) WHERE read_at IS NULL` — NOTI-007 뱃지용 partial 인덱스.
- `uq_notifications_event_member (event_id, member_id)` — Kafka at-least-once 중복 차단. `member_id`를 함께 묶는 이유는 `PROJECT_OPEN`처럼 이벤트 하나가 수신자 여러 명으로 팬아웃되기 때문이다.
- `updated_at`을 두지 않는다 — 유일한 변경이 `read_at`이고 그 자체가 시각이다.

### `notification_settings`

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `member_id` | UUID NOT NULL | FK 아님 |
| `notif_type` | VARCHAR(30) NOT NULL | |

- PK: `(member_id, notif_type)`. **행이 존재하면 수신 거부**이며 `enabled` 컬럼을 두지 않는다 — `enabled=true` 행은 기본값과 같은 값을 저장한 무의미한 행이다.
- 대리키 `id`를 두지 않는다 — id로 조회할 일이 없어, 자연키를 PK로 쓰면 인덱스 2개가 1개가 된다.
- 신규 회원에 대한 초기 데이터 생성이 필요 없다(행 없음 = 전체 수신).

### `live_notify_requests` (소유 서비스 미결)

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `member_id` | UUID | FK 아님 |
| `live_id` | UUID | FK 아님 — live-service 참조. **타입은 가정**(ERD 미확인) |

- PK: `(live_id, member_id)`. 신청/해제가 idempotent하려면 이 조합이 유일해야 한다. **`live_id`가 선두인 이유**: 화면 진입 시의 "내가 신청했나"는 1건 조회지만, LIVE 시작 시의 "신청자 전원"은 `live_id` 하나로 N건을 훑는다. `member_id`가 선두면 그 발송 쿼리가 전체 스캔이 된다.
- **받은 ERD SQL의 notification-service 섹션에 이 테이블이 없다** — 현재 ERD가 live-service 소유로 보고 있다는 뜻이다. 위 NOTI-002의 미결 박스 참고.

## 알림 유형 (`notif_type`)

`LIVE_START` / `COMMUNITY_ANSWER` / `SHIPPING_UPDATE` / `REFUND_STATUS` / `PROJECT_OPEN` / `REWARD_RESTOCK` / `COUPON_EXPIRING` / `SELLER_UPDATE_DUE`

각 유형의 알림 내용·수신자·출처는 `NotificationFunctionalSpec.md`의 "알림 유형" 표를 참고하세요.

## 에러 코드

`NotificationFunctionalSpec.md`의 "에러 코드 매핑" 절을 참고하세요. 엔드포인트별 상태 코드는 위 각 절의 Validation / Business Rules에 적혀 있습니다.

---

## 구현 착수 시 선행 조건

문서 범위 밖이지만, 빠뜨리면 **서비스가 기동조차 못 하거나 인증이 조용히 무방비가 되는** 항목이다.

1. **메인 클래스에 `@SpringBootApplication(scanBasePackages = "com.fundit")`**
   `@LoginUser CurrentUser`와 `InternalGatewaySecretFilter`는 `modules:common-webmvc`의 `CommonWebConfig`가 등록한다.
   기본 스캔 범위는 메인 클래스 패키지 하위뿐이라 이걸 넓히지 않으면 **리졸버도 필터도 등록되지 않는다** —
   기동은 성공하고 **요청 시점에** 깨진다(`@LoginUser` 주입 실패).
   이 서비스에 내부 전용 엔드포인트는 없지만 필터는 여전히 필요하다 — 모든 엔드포인트가 인증을 요구하므로 `X-User-Id`가 실려 오고, 그 헤더의 출처를 검증하는 게 이 필터다(없으면 위조가 그대로 통한다).
   (auth-service·project-service는 이 두 기능을 쓰지 않아 **일부러** 넓히지 않았다. notification-service는 둘 다 쓰므로 반대다.)

2. **모든 프로필(`local`/`dev`/`prod`)에 `internal-api.key` 선언**
   **이 서비스에 내부 전용 엔드포인트가 없어도 필요하다** — `CommonWebConfig`가 무조건
   `@Value("${internal-api.key}")`로 주입받기 때문에, 위 1번을 적용한 뒤 이 프로퍼티가 없으면
   **빈 생성 단계에서 기동이 실패한다.** `prod`에는 값이 아니라 `${INTERNAL_API_KEY}` 참조만 둔다.

3. **`PageResponse<T>`는 공용 모듈에 없다**
   `modules:common`이 아니라 서비스마다 `presentation/dto/PageResponse.java`로 각자 복사해 갖고 있다
   (member·order·project·payment 4곳). notification-service도 같은 방식으로 복사한다.

4. **`settings.gradle`의 서비스 배열에 `notification-service` 추가**
   배열에 이름이 없으면 폴더가 있어도 Gradle이 include하지 않는다.

5. **Kafka 의존성·컨슈머 설정**
   토픽명과 이벤트 스키마는 발행 측(order·payment)과 합의가 필요하다. 컨슈머는
   `infrastructure/event`의 `@KafkaListener` 어댑터로만 두고, 애플리케이션 계층은
   인바운드 포트 인터페이스만 의존한다(`RewardEventListener` 선례).

