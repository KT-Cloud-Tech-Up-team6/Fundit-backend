---
paths:
  - "**/infrastructure/event/**"
  - "**/application/**/*EventListener.java"
  - "**/application/**/*EventPublisher.java"
  - "**/common/event/**"
---

# 이벤트(Kafka) 계약 컨벤션

> 서비스 간 비동기 통신의 토픽명·payload·버저닝 규약입니다.
> 새 이벤트를 추가하려면 이 문서만 보고 토픽명을 정할 수 있어야 합니다.
> 토픽명 상수는 `modules:common`의 `KafkaTopics`가 정본입니다 — 문자열 리터럴을 각 서비스가 직접 들고 있지 않습니다.

## 왜 규약이 필요한가

토픽명은 **틀려도 예외가 나지 않습니다.** 발행자가 `notification.raised.v1`로 보내고 구독자가
`notification-raised-v1`을 듣고 있으면, 로그도 조용하고 알림만 영영 안 옵니다.

같은 실패를 이미 한 번 겪었습니다 — 게이트웨이와 서비스가 `X-Internal-Api-Key`를 각자 리터럴로
들고 있다가 안 맞아 회원가입이 100% 실패했고, 그래서 `modules:common`에 `AuthHeaders`가 생겼습니다.
`KafkaTopics`는 같은 이유로 같은 자리에 있습니다.

---

## 1. 토픽명 — `{도메인}.{사건}.v{N}`

- **사건은 과거형.** 이미 일어난 일을 알리는 것이므로 `succeeded`, `completed`, `signed-up`.
- **구분자는 `.`만.** `_`는 쓰지 않습니다 — Kafka가 메트릭 이름에서 `.`과 `_`를 같은 것으로 취급해
  충돌을 경고합니다(`reward.created`와 `reward_created`가 같은 메트릭이 됩니다).
- **단어 내부는 `-`.** `goal-failed`, `signed-up`, `funding-deadline-reached`.
- 전부 소문자.

```
reward.created.v1          ✅
funding.goal-failed.v1     ✅
reward_created.v1          ❌  _ 금지
Reward.Created.v1          ❌  대문자
reward.create.v1           ❌  과거형 아님
```

## 2. 토픽 목록

| 토픽 | 발행 | 구독 | 파티션 키 |
| --- | --- | --- | --- |
| `notification.raised.v1` | member / order / payment / fulfillment / project | notification | `memberId` |
| `member.signed-up.v1` | member | order (쿠폰 발급) | `memberId` |
| `project.wished.v1` | member | project (찜 통계) | `memberId` |
| `project.unwished.v1` | member | project (찜 통계) | `memberId` |
| `project.approved.v1` | project | search (색인 생성, SEARCH-011), member (찜 목록 스냅샷) | `projectId` |
| `project.updated.v1` | project | search (색인 갱신, SEARCH-011), member (찜 목록 스냅샷) | `projectId` |
| `reward.created.v1` | project | order (재고 동기화) | `rewardId` |
| `reward.updated.v1` | project | order (재고 동기화) | `rewardId` |
| `funding.succeeded.v1` | order | payment (정산), fulfillment | `fundingId` |
| `funding.goal-failed.v1` | order | payment (환불) | `fundingId` |
| `funding.cancelled-by-member.v1` | order | payment (환불) | `fundingId` |
| `payment.completed.v1` | payment | order (쿠폰) | `fundingId` |
| `refund.completed.v1` | payment | order (쿠폰) | `fundingId` |
| `project.funding-deadline-reached.v1` | project | order | `projectId` |
| `project.funding-reward-stats-updated.v1` | order | project (판매자 펀딩현황 rewardStats) | `projectId` |
| `shipping.completed.v1` | fulfillment | payment (정산) | `fundingId` |
| `shipment.shipped.v1` | fulfillment | order (판매자 발송목록 발송상태 필터·건수 캐시) | `fundingId` |
| `payment.reconciliation-required.v1` | 미정 | payment | `fundingId` |
| `live.started.v1` | live | notification (시작 알림) | `liveId` |
| `live.ended.v1` | live | AI 파트(질문요약·하이라이트 생성 트리거) | `liveId` |
| `live.questions-summarized.v1` | live | project (LIVE 검증 탭) | `liveId` |

목록은 **소비자 포트(`*EventListener`) 기준**으로 뽑았습니다. 아웃박스(발행 측)만 보면
아직 발행 코드가 없는 이벤트가 통째로 빠집니다.

**파티션 키는 순서를 보장하고 싶은 단위로 잡습니다.** 같은 키의 메시지는 같은 파티션에 들어가
순서가 보장됩니다. 알림은 한 회원 기준 순서가 중요해 `memberId`입니다.

### 기존 아웃박스 `event_type` → 토픽 대응

| 서비스 | `event_type` | 토픽 |
| --- | --- | --- |
| **member** | `PROJECT_WISHED` / `PROJECT_UNWISHED` | `project.wished.v1` / `project.unwished.v1` |
| **live** | `LIVE_STARTED` / `LIVE_ENDED` / `LIVE_QUESTIONS_SUMMARIZED` | `live.started.v1` / `live.ended.v1` / `live.questions-summarized.v1` |
| project | `REWARD_CREATED` / `REWARD_UPDATED` | `reward.created.v1` / `reward.updated.v1` |
| order | `FUNDING_SUCCEEDED` | `funding.succeeded.v1` |
| order | `FUNDING_GOAL_FAILED` | `funding.goal-failed.v1` |
| order | `FUNDING_CANCELLED_BY_MEMBER` | `funding.cancelled-by-member.v1` |
| payment | `PaymentCompleted` / `RefundCompleted` | `payment.completed.v1` / `refund.completed.v1` |
| **fulfillment** | `STALE_UPDATE_REMINDER`, `SCHEDULE_CHANGED`, `RECEIPT_AUTO_CONFIRMED` | **셋 다 `notification.raised.v1`** |

**fulfillment 3종은 예외입니다.** 이건 도메인 사실이 아니라 알림 명령이므로
`fulfillment.schedule-changed.v1` 같은 도메인 토픽을 만들지 **않습니다**. 구분은 payload의 `notifType`이 합니다
(3번 절 참고).

> **DB의 `event_type` 컬럼 값은 건드리지 않습니다.** 이 규약의 대상은 토픽명뿐입니다.
> 표기가 서비스마다 다른 건(`FUNDING_SUCCEEDED` vs `PaymentCompleted`) 알고 있지만,
> payment 아웃박스에 `CHECK (event_type IN ('PaymentCompleted','RefundCompleted'))` 제약이 걸려 있어
> 값을 통일하려면 마이그레이션이 필요한데 **그럴 이유가 없습니다.**

## 3. 알림은 `notification.raised.v1` 단일 토픽

여러 서비스가 발행하지만 토픽은 하나입니다. 도메인별로 쪼개지 않습니다.

쪼개면 notification이 각 도메인 이벤트를 해석해서 문구를 만들어야 하는데, 그건
`services/notification-service/CLAUDE.md`가 금지한 것입니다 —
*"이 서비스는 **무엇을 알릴지 판단하지 않습니다.** 타 서비스가 누구에게 어떤 알림을 이벤트로 던지고,
이 서비스는 받아서 쌓고 보여줍니다."*

**발행 측이 완성된 문구를 보냅니다.** 구분은 payload의 `notifType`이 합니다.

## 4. payload — 봉투 없이 평평한 JSON

`{eventId, type, occurredAt, payload}` 같은 래퍼를 두지 않습니다.
`type`은 토픽명과 중복이고(토픽 1개 = 이벤트 1종), `occurredAt`은 Kafka 레코드 타임스탬프와 중복입니다.
남는 게 `eventId` 하나라 그냥 필드로 둡니다.

```json
// notification.raised.v1
{
  "eventId": "fulfillment:4521",
  "memberId": "0199a1b2-...",
  "notifType": "SHIPPING_UPDATE",
  "title": "「무선 이어폰 프로젝트」 배송이 '출고' 단계로 넘어갔어요",
  "relatedUrl": "/my/fundings/1/shipping"
}
```

**이벤트 레코드 클래스는 공유하지 않습니다.** 각 서비스가 자기 record를 선언하고 javadoc에
"동일 계약"임을 적습니다(order `RewardEventListener.RewardCreatedEvent` ↔
project `RewardEventPublisher.RewardCreatedEvent`가 이미 이 형태). 공유하면 생산자·소비자가
같이 배포돼야 하는 잠금이 생깁니다. **JSON이 계약이고, 공유하는 건 토픽명뿐입니다.**

## 5. `eventId` — `"{service}:{outboxId}"`

중복 소비 차단(멱등)의 근거입니다. Kafka는 at-least-once라 같은 메시지가 두 번 옵니다.

project / order / payment / fulfillment 아웃박스가 전부 `id BIGINT GENERATED ALWAYS AS IDENTITY PK`라
워커가 재발행해도 값이 변하지 않습니다. 새 컬럼 없이 그대로 씁니다.

```
"order:1042"    "payment:77"    "fulfillment:4521"
```

> ⚠️ **Kafka 좌표(topic-partition-offset)를 eventId로 쓰지 마세요.** 재발행 시 값이 달라져
> 중복 차단이 조용히 무력화됩니다.

소비 측은 이 값으로 멱등을 보장합니다. 예: `notifications` 테이블의
`(event_id, member_id)` UNIQUE + `ON CONFLICT DO NOTHING`.

## 6. 버저닝 — `api-convention.md`와 같은 규칙

새로 만들지 않고 REST에 이미 적용 중인 규칙을 그대로 씁니다.

| 변경 | 버전 |
| --- | --- |
| 필드 **추가** | 그대로 (`.v1` 유지) — 소비자는 모르는 필드를 무시하도록 구현 |
| 필드 **삭제 · 이름 변경 · 타입 변경** | `.v2` 새 토픽으로 전환, 구버전은 최소 유지 기간 후 폐기 |

**소비자는 모르는 필드를 무시해야 합니다.** 이게 지켜져야 발행 측이 필드를 추가할 때
구독자를 깨뜨리지 않습니다. 역직렬화 설정에서 unknown field를 에러로 두지 마세요.

**적용 예 — `FundingSucceeded`의 현재 불일치**

payment `FundingSucceededListener`는 정산에 `(fundingId, projectId, sellerId, achievedAt)`을 기대하는데,
발행 측 order `FundingEventPublisher`는 `(fundingId, projectId)`만 보냅니다. fulfillment는 두 필드를
안 쓰므로 영향이 없습니다.

→ **필드 추가이므로 `.v1`을 유지한 채 발행 측이 `sellerId`·`achievedAt`을 채웁니다.**
소비자가 모르는 필드를 무시하는 게 전제이므로 fulfillment는 바꿀 게 없고, 새 토픽도 필요 없습니다.

## 7. 실패 처리

- **역직렬화 실패·검증 실패는 해당 메시지만 실패로 남기고 컨슈머는 계속 진행합니다.**
  한 건 때문에 파티션 전체가 멈추면 안 됩니다.
- 재처리가 필요한 실패는 멱등 보장(`eventId`)에 기대어 재시도합니다.

---

## 새 이벤트를 추가할 때

1. 이 문서 1번 형식으로 토픽명을 정한다
2. `modules:common`의 `KafkaTopics`에 상수를 추가한다 (리터럴을 서비스에 두지 않는다)
3. 위 2번 표에 한 줄 추가한다 (발행·구독·파티션 키)
4. 발행 측은 아웃박스에 적재하고, `eventId`는 `"{service}:{outboxId}"`로 만든다
5. 소비 측은 `eventId` 기준 멱등을 보장한다
