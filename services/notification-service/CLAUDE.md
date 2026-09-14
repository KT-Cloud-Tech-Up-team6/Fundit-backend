# notification-service

> 루트 `CLAUDE.md`(레포 공통 규칙)와 `.claude/rules/`를 전제로, 여기는 notification-service에만 해당하는 내용만 다룹니다.

## 이 서비스가 하는 일
온사이트 알림함 — 알림 적재·조회·안읽음 개수·읽음 처리·수신설정, LIVE 시작 알림 신청. (`PRD.md` 2. 도메인/서비스 개요 기준)

**MVP 채널은 온사이트 하나입니다.** PM 문서상 보조 채널로 정의된 웹푸시·이메일·SMS는 이번 범위가 아닙니다.
auth-service의 인증 메일·SMS(AUTH-009 이메일 찾기, AUTH-010 재설정 링크)는 인증·인가 플로우의 일부이고 이 서비스가 해당 본문 템플릿을 갖지 않으므로 **auth-service 소관**입니다.

이 서비스는 **무엇을 알릴지 판단하지 않습니다** — 타 서비스가 "누구에게 어떤 알림을" 이벤트로 던지고, 이 서비스는 받아서 쌓고 보여줍니다.

## 먼저 읽을 문서
알림 관련 작업을 시작하기 전에 **`services/notification-service/docs/NotificationFunctionalSpec.md`를 먼저 읽으세요** — NOTI-001~007 기능별 요구사항, 알림 유형 8종, 문구 예시, 예외 처리, 에러 코드 매핑표, **미확정/보류 사항 + 협의처**가 있습니다. 이 CLAUDE.md는 그 문서의 핵심만 요약한 것이지 대체하지 않습니다.

API 계약은 `NotificationDomainApiSpec.md`, DDL 정본은 `notification-schema.sql`입니다(ERD 초안 대비 변경 내역이 파일 하단 주석에 있음).

> 확인 상태: 전송 방식은 **Kafka 확정**(2026-09-11, order·payment 담당자 합의). 채널 범위·알림 유형 8종·수신설정 구조·스키마는 PM 검토 완료.
> 남은 🔴 2건은 이벤트 계약(`memberId`·`event_id`)으로 order·payment 담당자와 합의해야 하며, 이것 없이도 Kafka 어댑터를 뺀 나머지는 구현 가능합니다. 프론트 확인 2건(`relatedUrl` 경로 규칙, `body` 컬럼 삭제 판단)은 알림함 화면 설계 시점에 맞추면 됩니다 — 상세는 기능명세서 "미확정/보류 사항" 표.

## 도메인 테이블 (스키마 확정됨)
- `notifications` — `id`, `event_id`(발행 측이 싣는 이벤트 고유 ID), `member_id`(UUID, member-service 참조 — **FK 아님**), `notif_type`, `title`(100자), `related_url`, `read_at`, `created_at`. **`body` 컬럼은 없음** — 알림함이 제목 한 줄이라는 전제로 뺐다(프론트가 2줄 레이아웃이면 되살릴 것). `updated_at`도 없음 — 유일한 변경이 `read_at`이고 그 자체가 시각이다.
- `notification_settings` — `(member_id, notif_type)` 복합 PK **2컬럼뿐**. 행이 존재하면 그 유형을 수신 거부한다.
- `live_notify_requests` — **소유 서비스 미결**. ERD는 live-service DB에 두고 있으나 live-service는 개발 착수 전이다. 이 문서 권고안은 notification-service 소유. PK는 `(live_id, member_id)` — 발송 시 `live_id` 하나로 N건을 훑기 때문에 `member_id`가 선두면 전체 스캔이 된다.

> **인덱스 3개**: `(member_id, created_at DESC)` 목록 조회용 / `(member_id) WHERE read_at IS NULL` 안읽음 개수용 partial / `(event_id, member_id)` UNIQUE 중복 소비 차단용. `notif_type`에 DB CHECK 제약은 두지 않는다 — 레포가 열거값에 CHECK를 쓰지 않는 관행(`accounts.role`, `reward_event_outbox.event_type`)을 따르고 값 검증은 앱이 한다.

## 핵심 설계 결정 (구현 시 반드시 지킬 것)
- **입구는 Kafka 컨슈머 하나**: 알림을 만드는 경로는 NOTI-006 단 하나고, **수신자(`memberId`)가 이미 채워진 이벤트만** 받는다. `fundingId`로 참여자를 되물으면 알림 도메인이 주문 도메인에 동기 의존하게 되고, 그 도메인이 바뀔 때마다 알림이 함께 흔들린다. 구조는 order-service의 `RewardEventListener`를 그대로 따른다 — `application`에 인바운드 포트 + record 계약, `infrastructure/event`에 `@KafkaListener` 어댑터.
- **멱등은 DB 제약으로**: Kafka는 at-least-once고 발행 측 아웃박스도 "전송 성공 후 `published_at` 기록 전 장애"면 재발행한다. **중복 알림은 사용자가 즉시 본다.** `(event_id, member_id)` UNIQUE로 막고 충돌은 무시한다 — 컨슈머에 중복 판정 로직을 따로 짜지 않는다. `member_id`를 함께 묶는 이유는 `PROJECT_OPEN`처럼 이벤트 하나가 수신자 여러 명으로 팬아웃되기 때문이다(단독 UNIQUE면 두 번째 수신자부터 제약 위반).
- **수신설정은 "행 존재 = 거부"**: `enabled` 컬럼을 두지 않는다 — `enabled=true` 행은 기본값과 같은 값을 저장한 무의미한 행이다. 기본값이 전체 수신이라 신규 회원 초기 데이터가 필요 없고, 설정 변경이 insert/delete로 끝난다.
- **두 테이블 모두 단순 애그리거트**: `persistence-convention.md` 복잡도 표가 `NotificationSetting`을 단순 애그리거트 예시로 이미 등재해뒀다. `Notification`도 값 저장·조회와 `read_at` 1회 기록이 전부라 같은 분류다. 규약 2번 방식대로 `infrastructure/persistence/{aggregate}/`에 `JpaEntity` + `JpaRepository`만 둔다.
- **타인 알림 접근은 404**: 읽음 처리에서 타인의 알림 ID를 받으면 403이 아니라 **404**로 응답한다 — 403은 "그 알림이 존재한다"를 알려주므로 ID를 넣어보며 타인 알림 존재 여부를 캐낼 수 있다(`security.md` S10).
- **[천장] 온사이트 전용인 동안만 이 구조로 충분하다**: 지금은 이벤트 하나의 사이드이펙트가 `notifications` INSERT 하나뿐이라 위 UNIQUE로 끝난다. 이메일·웹푸시가 붙으면 사이드이펙트가 둘이 되어 "INSERT 성공 → 발송 전 장애 → 재수신 시 UNIQUE로 스킵 → **발송 영영 유실**"이 생긴다. 그때는 발송 상태 컬럼(`sent_at` 등) + 재시도 워커가 필요하다.

## 에러 코드
도메인 전용 코드는 `NotificationErrorCode implements ErrorCode`로 만든다(서비스당 flat enum 1개 — `error-handling.md` 컨벤션). 다만 **MVP 범위에서 이 서비스 고유의 도메인 에러는 없다** — `NOT_FOUND`(타인/없는 알림), `INVALID_INPUT`(정의되지 않은 `notifType`), `UNAUTHORIZED`가 전부이고 모두 `CommonErrorCode`에 이미 있으니 재정의하지 않는다. 새로 만들기 전에 기능명세서의 "에러 코드 매핑" 표부터 확인할 것.

NOTI-006은 Kafka 컨슈머라 HTTP 응답이 없다 — 처리 실패는 해당 메시지만 실패로 남기고 컨슈머는 계속 진행한다.

## 이 서비스에서 절대 하지 말아야 할 것
- **수신자를 타 서비스에 되묻지 말 것** — `fundingId`/`projectId`로 참여자·찜한 회원 목록을 조회하지 않는다. 이벤트에 `memberId`가 채워져 오지 않으면 그건 발행 측이 고칠 일이다
- **알림을 생성하는 REST 엔드포인트를 만들지 말 것** — 입구는 Kafka 컨슈머 하나다. 브로커 확정 전 설계했던 `POST /api/v1/notifications`는 **의도적으로 삭제**했다. 입구가 둘이면 게이트웨이 차단·내부 키 검증이 따라붙고, 같은 경로의 `GET`(알림함 조회)과 충돌 위험도 생긴다
- **`event_id` 없이 알림을 INSERT하지 말 것** — NOT NULL이고 멱등의 유일한 근거다. 임시로 채워야 하면 Kafka 좌표(topic-partition-offset)를 쓸 수 있으나 재발행 시 값이 달라져 중복이 막히지 않는다는 걸 알고 쓸 것
- **`notification_settings`에 `channel`·`enabled` 컬럼을 되살리지 말 것** — 채널이 온사이트 하나인 동안은 쓰이지 않는 분기다. 채널이 실제로 늘어나는 시점에 `channel`을 추가하면 되고, 그때도 API 요청/응답 형태는 바뀌지 않는다
- **`domain` 패키지·Mapper·PersistenceAdapter·리포지토리 포트를 만들지 말 것** — 두 테이블 모두 단순 애그리거트다(위 참고). 나중에 불변식이나 상태 전이가 실제로 생기면 그때 규약 1번 방식으로 전환한다
- **알림 문구에서 변수 바로 뒤에 조사를 붙이지 말 것** — 한국어 조사는 앞 글자 받침에 따라 갈린다(`프로젝트`**가** / `이어폰`**이**). `{프로젝트명}이(가)`로 쓰면 화면에 `무선 이어폰 프로젝트이(가)`가 그대로 노출된다. 변수 뒤에 고정 명사를 하나 끼워(`「{프로젝트명}」 배송이`) 조사가 그 명사에 붙게 한다
- **이메일·SMS 본문 템플릿을 만들지 말 것** — MVP는 온사이트만이고, auth-service의 인증 메일·SMS는 auth 소관이다
- **`X-User-Id` 헤더를 직접 파싱하지 말 것** — `@LoginUser CurrentUser`로 주입받는다(루트 `CLAUDE.md` 공통 규칙)
