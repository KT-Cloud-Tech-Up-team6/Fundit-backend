# Notification 기능 명세서

> notification-service의 상세 기능 명세입니다. 서비스 간 흐름·책임 경계는 루트 `docs/PRD.md`, 공통 규칙(에러코드/보안 등)은 `.claude/rules/`를 참고하세요.
> 출처: PM 요구사항정의서(14.5 알림함 / 10.4 찜하기 / 11.1 LIVE 목록 / 7.2 커뮤니티 관리 / 8.1~8.2 제작·배송 / 15.x 환불 / 16.2 쿠폰함) + 사내 초안(NOTI-001~005)
> API 계약은 `NotificationDomainApiSpec.md`를 참고하세요.

## 범위

이 문서는 notification-service가 담당하는 **온사이트 알림 적재·조회·안읽음 개수·읽음 처리·수신설정·LIVE 알림 신청**을 다룹니다.

**MVP 채널은 온사이트 알림함 하나입니다.** PM 문서상 보조 채널로 정의된 웹푸시·이메일·SMS는 이번 범위에 포함하지 않습니다.
auth-service의 인증 메일·SMS(AUTH-009 이메일 찾기, AUTH-010 재설정 링크)는 인증·인가 플로우의 일부이자 알림 도메인이 본문 템플릿을 갖지 않으므로 **auth-service 담당**입니다.

**알림 트리거는 전부 Kafka로 받습니다**(2026-09-11 order·payment 담당자 합의). 스키마는 `notification-schema.sql` 참고.

> **작성 경위**: 초기 검토에서 order/payment의 이벤트 레코드(`FundingSucceededEvent`, `PaymentCompletedEvent` 등)를 역추론해 펀딩 성공·결제 완료·심사 승인을 알림 항목으로 잡았으나, PM 14.5.3이 정의한 알림 유형 목록에 없는 항목이었습니다. **PM 14.5.3의 유형 목록을 기준으로 재작성**하고, 여기에 기존 레포 문서가 이미 notification-service에 넘겨둔 3건(`OrderDomainApiSpec.md:250` 재입고 알림, `OrderDomainApiSpec.md:354` 쿠폰 만료 임박, `PaymentFunctionalSpec.md:202` 재고 확보 실패 자동 환불 안내)을 추가했습니다.

## 기능 목록

### 알림 신청

#### NOTI-001. 오픈예정 프로젝트 찜 알림 연동
| 항목 | 내용 |
|---|---|
| PRD 코드 | FL_B_HM_01_04 |
| 권한 | 구매자 |
| 우선순위 | P1 |
| 트리거 | 이벤트 구독 |

- **요구사항**: 오픈 예정 프로젝트를 찜한 회원에게 오픈 시점 알림을 발송한다.
- **처리 내용**: 프로젝트 오픈 시점에 찜한 회원 각각에게 `PROJECT_OPEN` 알림 생성. **찜 목록 조회는 notification-service가 하지 않는다** — 아래 검토의견 참고
- **입력값**: 수신자가 채워진 알림 이벤트(`memberId`, `PROJECT_OPEN`, 프로젝트 정보)
- **출력값**: 없음 (컨슈머)
- **예외 처리**: 대상 회원 0명 → 발송 없이 정상 종료 / 발송 실패 건은 재시도 대상으로 남김
- **보안/권한** (S1·S4): 이벤트 처리 시 바인딩 변수 사용(S1) · 수신자는 이벤트에 실려온 값만 사용하고 요청자 입력을 신뢰하지 않음(S4)
- **검토의견(변경사항)**: 초안의 처리 내용은 "프로젝트 오픈 이벤트 구독 후 해당 프로젝트를 찜한 회원 목록 조회해 알림 발송"이었으나, 찜 데이터(`wishes`)는 member-service 소유라 notification-service가 되물으면 동기 의존이 생깁니다. **member-service가 프로젝트 오픈 이벤트를 구독해 자기 `wishes`를 훑고 수신자별 알림 이벤트를 발행하는** 방향을 권고합니다(홉 2개, 데이터 소유자가 직접 조회). P1이라 MVP 구현 대상은 아니며 착수 시점에 확정 필요.

#### NOTI-002. LIVE 알림 신청/해제
| 항목 | 내용 |
|---|---|
| PRD 코드 | FL_B_LV_01_01 |
| 권한 | 구매자 |
| 우선순위 | **MVP** |
| 트리거 | API 호출 |

- **요구사항**: 오픈 예정 LIVE의 시작 전 알림을 신청·해제한다.
- **처리 내용**: `live_notify_requests`에 `(live_id, member_id)` upsert(신청) / delete(해제). LIVE 시작 시 이 목록을 수신자로 `LIVE_START` 알림 생성
- **입력값**: liveId
- **출력값**: 신청 상태(`notifying`)
- **예외 처리**: 이미 신청/해제된 상태로 재요청 → 같은 결과 반환(idempotent, 실패로 처리하지 않음)
- **보안/권한** (S4): 본인 알림 신청만 처리 가능 — `@LoginUser CurrentUser`의 회원 ID만 사용하고 요청 본문의 회원 식별자는 받지 않음
- **검토의견(변경사항)**: 초안이 지적한 대로 ERD상 신청 데이터(`streaming.live_notify_requests`)는 live-service DB에 있습니다. 다만 **live-service는 아직 개발에 착수하지 않아 코드가 존재하지 않습니다**(`settings.gradle` 서비스 배열에 미포함) — 방송 운영 여부가 아니라 서비스 코드가 아직 없다는 뜻입니다. 담당자도 아직 정해지지 않았습니다.
  **신청 데이터를 notification-service가 소유하는 쪽을 권고**합니다. 역할을 이렇게 나눕니다:

  | 서비스 | 역할 |
    | --- | --- |
  | live-service | "이 LIVE가 시작됐다"는 **사실 하나만** 이벤트로 발행 |
  | notification-service | 누가 알림을 신청했는지 보관하고, 그 사람들에게 **발송** |

  상하관계가 아니라 역할 분담입니다 — 방송은 방송만 알고, 누구에게 어떻게 알릴지는 알림이 압니다.
  반대로 두면(신청 데이터가 live-service에 있으면) 알림을 보낼 때마다 live-service에 "누가 신청했나요"를 물어야 해서 호출이 한 번 더 늘고, 그 서비스가 죽으면 알림도 못 나갑니다.
  **ERD 확정 권한이 이 문서에 없어 미결이며, live-service 착수 시점에 정하면 됩니다.**

### 알림함

#### NOTI-003. 알림 목록 조회
| 항목 | 내용 |
|---|---|
| PRD 코드 | FL_B_MY_04_01 |
| 권한 | 구매자 |
| 우선순위 | **MVP** |
| 트리거 | API 호출 |

- **요구사항**: LIVE 시작, 커뮤니티 답변, 배송상태 변경 등의 알림을 확인한다.
- **처리 내용**: 본인 알림을 최신순 조회. 유형(`notif_type`)·읽음 여부(`read_at`)를 함께 반환해 화면에서 필터·뱃지 처리
- **입력값**: 페이지네이션(page, size)
- **출력값**: 알림 목록(`PageResponse`)
- **예외 처리**: 로드 실패 → 안내+재시도 / 알림 0건 → Empty State
- **보안/권한** (S4): 본인 알림만 조회 가능 — 조회 조건의 회원 ID는 인증 컨텍스트에서만 가져옴

#### NOTI-004. 알림 수신설정 변경
| 항목 | 내용 |
|---|---|
| PRD 코드 | FL_B_MY_04_01 |
| 권한 | 구매자 |
| 우선순위 | **MVP** |
| 트리거 | API 호출 |

- **요구사항**: 알림 유형별 수신 여부(온/오프)를 설정한다.
- **처리 내용**: `notification_settings`에 `(member_id, notif_type)` 행이 **존재하면 수신 거부**. `enabled=false`면 insert, `true`면 delete. 기본값은 "행 없음 = 수신"
- **입력값**: notifType, enabled
- **출력값**: 저장 결과
- **예외 처리**: 정의되지 않은 `notifType` → 400
- **보안/권한** (S4): 본인 설정만 변경 가능
- **검토의견(변경사항)**: 초안은 `(member_id, notif_type, channel)` 3키 upsert에 `enabled` 컬럼을 두고 `channel`을 `ONSITE`/`PUSH`/`EMAIL`/`SMS`로 받는 구조였습니다. **MVP 채널이 온사이트 하나뿐이라 `channel`은 단일값만 들어가고 나머지 3개 enum 값은 쓰이지 않는 분기입니다.** `channel` 축과 `enabled` 컬럼을 빼고 "행 존재 = 거부"로 단순화했습니다 — 채널이 늘어나는 시점에 `channel` 컬럼을 추가하면 되고, **요청/응답 형태는 그대로라 API 계약은 바뀌지 않습니다.**

#### NOTI-005. 알림 읽음 처리
| 항목 | 내용 |
|---|---|
| PRD 코드 | FL_B_MY_04_01 |
| 권한 | 구매자 |
| 우선순위 | **MVP** |
| 트리거 | API 호출 |

- **요구사항**: 알림을 읽음 상태로 변경한다.
- **처리 내용**: `notifications.read_at`을 최초 1회만 기록. 이미 값이 있으면 덮어쓰지 않음
- **입력값**: notificationId
- **출력값**: 처리 결과(`notificationId`, `readAt`)
- **예외 처리**: 이미 읽은 알림 재요청 → 기존 `readAt` 그대로 반환(idempotent) / 존재하지 않거나 타인 알림 → 404
- **보안/권한** (S4·S10): 본인 알림만 처리 가능 · 타인 알림 ID 조작 시 403이 아닌 **404**로 응답해 알림 존재 여부를 노출하지 않음(S10)

#### NOTI-007. 안읽음 개수 조회
| 항목 | 내용 |
|---|---|
| PRD 코드 | FL_B_MY_04_01 |
| 권한 | 구매자 |
| 우선순위 | **MVP** |
| 트리거 | API 호출 |

- **요구사항**: 알림함 아이콘에 표시할 안 읽은 알림 개수를 조회한다.
- **처리 내용**: `read_at IS NULL`인 본인 알림 건수를 센다. `(member_id) WHERE read_at IS NULL` partial 인덱스로 처리
- **입력값**: 없음
- **출력값**: 안읽음 개수(`unreadCount`)
- **예외 처리**: 로드 실패 → 뱃지 미표시(알림함 진입 자체를 막지 않음)
- **보안/권한** (S4): 본인 알림만 집계
- **검토의견(변경사항)**: 사내 초안·PM 요구사항정의서 모두에 없던 항목이나, **NOTI-003 목록만으로는 전체 안읽음 개수를 구할 수 없어**(페이징되므로 해당 페이지 안에서만 셀 수 있음) PM 검토 의견에 따라 추가했습니다. 뱃지는 알림함 밖(홈 등)에서도 보여야 하므로 목록 응답에 얹지 않고 별도 엔드포인트로 둡니다.

### 시스템

#### NOTI-006. 알림 이벤트 수신
| 항목 | 내용 |
|---|---|
| PRD 코드 | — (화면 없음) |
| 권한 | 시스템 |
| 우선순위 | **MVP** |
| 트리거 | Kafka 이벤트 구독 |

- **요구사항**: 타 서비스가 발행한 알림 이벤트를 수신해 대상 회원의 알림함에 적재한다.
- **처리 내용**: `notification_settings` 확인 후 `notifications` 행 생성. **수신자(`memberId`)는 이벤트에 이미 채워져 와야 하며, notification-service가 타 서비스에 되묻지 않는다**
- **입력값**: eventId, memberId, notifType, title(100자 이내), relatedUrl
- **출력값**: 없음 (컨슈머)
- **예외 처리**: 이미 처리한 이벤트 재수신 → 무시(아래 멱등 처리) / 수신자가 해당 유형을 수신 거부 → 생성하지 않고 정상 처리 / 정의되지 않은 `notifType` → 해당 메시지만 실패 처리하고 컨슈머는 계속 진행
- **보안/권한** (S1): 이벤트 처리 시 바인딩 변수 사용. 수신자는 이벤트에 실려온 값만 쓰고 외부 입력을 신뢰하지 않는다 — 이 경로는 게이트웨이를 거치지 않으므로 외부에서 직접 호출할 방법 자체가 없다
- **멱등 처리 (필수)**: Kafka는 at-least-once다. 발행 측 아웃박스도 "전송 성공 후 `published_at` 기록 전 장애"면 재발행하므로 중복은 예정된 일이고, **중복 알림은 사용자가 즉시 본다.** `notifications (event_id, member_id)` UNIQUE 제약으로 막고 충돌은 무시한다 — 컨슈머에 중복 판정 로직을 따로 짜는 것보다 짧고 정확하다. `member_id`를 함께 묶는 이유는 `PROJECT_OPEN`처럼 이벤트 하나가 수신자 여러 명으로 팬아웃되는 경우가 있기 때문이다.
- **검토의견(변경사항)**: 초안에 없던 항목입니다. 초안 5개는 모두 화면 기준(조회·설정·신청)이라 **알림이 생성되는 경로 자체가 정의돼 있지 않았습니다.** 브로커 확정 전에는 내부 전용 REST 엔드포인트(`POST /api/v1/notifications`)로 설계했으나, **Kafka 확정으로 삭제**했습니다 — 입구가 둘일 이유가 없고, 게이트웨이 경로 차단·내부 키 검증 두 겹 방어도 함께 불필요해집니다.

## 수신 계약 — 알림이 들어오는 경로

알림 도메인의 입구는 **Kafka 컨슈머 하나(NOTI-006)** 입니다. 수신자(`memberId`)가 이미 채워진 이벤트만 받습니다.

레포에 이미 같은 모양의 인바운드 포트 선례가 있습니다 — `order-service`의 `application/inventory/RewardEventListener`:

> "브로커가 정해지면 `infrastructure/event`에 어댑터만 추가해 이 포트를 호출하면 된다."

발행 측도 이미 이 구조입니다(order `FundingEventOutboxWorker` + `FundingEventTransport`, project `reward_event_outbox` + `RewardEventTransport`). 알림은 그 반대편인 **컨슈머만** 만들면 됩니다:

- `application/notification/NotificationEventListener` — 인바운드 포트. 이벤트 계약을 record로 명시
- `infrastructure/event/` — `@KafkaListener` 어댑터. 이 포트를 호출하기만 함

**수신자 조회를 notification-service가 하지 않는 이유**: `fundingId`로 참여자를 되물으면 알림 도메인이 주문 도메인에 동기 의존하게 되고, 그 도메인이 바뀔 때마다 알림이 함께 흔들립니다.

**선행 조건(별도 이슈)**: 현재 order/payment의 이벤트 레코드에는 수신자도 이벤트 ID도 없습니다 — `FundingSucceededEvent(fundingId, projectId)`, `PaymentCompletedEvent(paymentId, fundingId, couponIssuanceId, paidAt)`. 발행 측이 `memberId`와 `eventId`를 실어주지 않으면 알림 대상을 정할 수도, 중복을 막을 수도 없습니다.

## 알림 유형 (`notif_type`)

| notif_type | 알림 내용 | 수신자 | 출처 |
| --- | --- | --- | --- |
| `LIVE_START` | LIVE 시작 | 알림 신청자 | PM 14.5.3 / 11.1.4 (NOTI-002) |
| `COMMUNITY_ANSWER` | 커뮤니티 질문에 판매자 답변 등록 | 글 작성자 | PM 14.5.3 / 7.2.4 / 12.4.3 |
| `SHIPPING_UPDATE` | 제작·배송 단계 변경, 진행 내용 업데이트, 일정 변경·지연 사유 등록 | 펀딩 참여자 | PM 14.5.3 / 14.3.4 / 8.2.4 |
| `REFUND_STATUS` | 환불 상태 변경(승인·완료·반려), 미달 자동 환불, 참여 취소 완료 | 구매자 | PM 14.5.3 / 14.4.4 / 15.1.4 / 15.2.4 / `PaymentFunctionalSpec.md:202` |
| `PROJECT_OPEN` | 찜한 오픈예정 프로젝트 오픈 | 찜한 회원 | PM 14.5.3 / 10.4.4 (NOTI-001) |
| `REWARD_RESTOCK` | 품절 리워드 재입고·재오픈 | 재입고 알림 신청자 | `OrderDomainApiSpec.md:250` (ORDER-011) |
| `COUPON_EXPIRING` | 보유 쿠폰 유효기간 만료 임박 | 쿠폰 보유자 | `OrderDomainApiSpec.md:354` / PM 16.2.3 |
| `SELLER_UPDATE_DUE` | 제작·배송 진행 내용 업데이트 주기 도래(미등록) | 판매자 | PM 8.1.3 / 14.3.3 |

> **PM 14.5.3의 "제작·배송 단계 변경 / 제작 진행 내용 업데이트 / 일정 변경·지연 사유 등록" 3종은 `SHIPPING_UPDATE` 하나로 묶었습니다.** 수신설정이 유형 단위라 3개로 쪼개면 "배송 알림 끄기"를 세 번 해야 합니다. PM 목록의 3종은 알림 문구가 세 가지라는 뜻이지 설정 축이 세 개라는 뜻은 아니라고 판단했습니다(초안의 `SHIPPING_UPDATE`와도 일치). 세부 구분은 문구·`relatedUrl`로 처리합니다.

## 알림 문구 예시 (목업)

실제 알림함에 어떻게 보이는지 — `title`은 100자 이내, `relatedUrl`은 탭할 때 이동할 화면이다.
`{}`는 이벤트에서 채워지는 값이고, 프로젝트 제목은 40자 제한(PM 5.1.4.1)이라 문구에 끼워도 여유가 있다.

> **작성 규칙 — 변수 바로 뒤에 조사를 붙이지 않는다.**
> 한국어 조사는 앞 글자 받침에 따라 갈리므로(`프로젝트`**가** / `이어폰`**이**) `{프로젝트명}이(가)`처럼 쓰면
> 화면에 `무선 이어폰 프로젝트이(가)`가 그대로 노출된다. 변수 뒤에 **고정 명사를 하나 끼워**
> (`「{프로젝트명}」 배송이`, `「{리워드명}」 리워드를`) 조사가 그 명사에 붙게 하면 코드 없이 해결된다.

| notif_type | title | relatedUrl |
| --- | --- | --- |
| `LIVE_START` | 「{프로젝트명}」 LIVE가 시작됐어요 | `/live/{liveId}` |
| `COMMUNITY_ANSWER` | 문의하신 질문에 답변이 등록됐어요 | `/projects/{projectId}/community` |
| `SHIPPING_UPDATE` | (아래 3가지 상황 — 배송 섹션 참고) | `/my/fundings/{fundingId}/shipping` |
| `REFUND_STATUS` | 환불이 완료됐어요 · 환불 신청이 접수됐어요 · 환불 신청이 반려됐어요 | `/my/refunds/{refundId}` |
| `PROJECT_OPEN` | 찜하신 「{프로젝트명}」 펀딩이 오픈했어요 | `/projects/{projectId}` |
| `REWARD_RESTOCK` | 품절됐던 「{리워드명}」 리워드를 다시 펀딩할 수 있어요 | `/projects/{projectId}?reward={rewardId}` |
| `COUPON_EXPIRING` | 보유하신 쿠폰이 {N}일 뒤 만료돼요 | `/my/coupons` |
| `SELLER_UPDATE_DUE` | 「{프로젝트명}」 진행 상황 업데이트가 필요해요 | `/seller/projects/{projectId}/delivery` |

### 배송 관련 문구 (PM 요청 — 배송 UX/UI 발표용)

PM 14.5.3이 배송 알림을 3가지로 나눴고 이 문서는 `SHIPPING_UPDATE` 하나로 묶었다.
설정 축만 하나일 뿐 **문구는 상황별로 다르게 나간다**:

| 상황 | 수신자 | title |
| --- | --- | --- |
| 단계 변경 (PM 14.3.4) | 펀딩 참여자 | 「{프로젝트명}」 배송이 '{단계}' 단계로 넘어갔어요 |
| 진행 내용 업데이트 (PM 8.1.3) | 펀딩 참여자 | 「{프로젝트명}」에 새로운 진행 소식이 올라왔어요 |
| 일정 변경·지연 (PM 8.2.4) | 펀딩 참여자 | 「{프로젝트명}」 배송 일정이 변경됐어요 ({변경일}) |
| 업데이트 주기 도래·미등록 (PM 8.1.3) | **판매자** | 「{프로젝트명}」 진행 상황 업데이트가 필요해요 |

`{단계}`는 '제작 착수 / 생산 / 검수 / 출고 / 배송' 5단계 중 하나다(PM 8.1.3 표준).
마지막 행만 `SELLER_UPDATE_DUE`(판매자 수신)이고 나머지 셋은 `SHIPPING_UPDATE`(구매자 수신)다.

알림함에서의 한 줄 렌더링:

```
┌──────────────────────────────────────────────┐
│ ● 「무선 이어폰 프로젝트」 배송이 '출고'       │
│    단계로 넘어갔어요                   3시간 전│
└──────────────────────────────────────────────┘
   ● = 안 읽음 표시(read_at IS NULL) · 탭하면 제작·배송 현황(FL_B_MY_02_01)으로 이동
```

## 에러 코드 매핑

도메인 전용 코드는 서비스당 flat enum 1개(`NotificationErrorCode implements ErrorCode`)로 만듭니다(`error-handling.md`).
다만 **MVP 범위에서 notification-service 고유의 도메인 에러는 없습니다** — 아래로 전부 덮입니다.
NOTI-006은 Kafka 컨슈머라 HTTP 응답이 없습니다 — 처리 실패는 해당 메시지만 실패로 남기고 컨슈머는 계속 진행합니다.

| 상황 | 에러 코드 | 해당 기능 |
| --- | --- | --- |
| 존재하지 않거나 타인의 알림 | `CommonErrorCode.NOT_FOUND` (404, 기존) | NOTI-005 |
| 정의되지 않은 `notifType`, 필수값 누락 | `CommonErrorCode.INVALID_INPUT` (400, 기존) | NOTI-004 |

## 미확정/보류 사항 (한눈에 보기)

🔴 = 알림 착수 전 반드시 합의돼야 하는 것 / ⚠️ = 구현 중 필요 / ⏸ = 해당 서비스·기능 착수 시점으로 미룸

| 항목 | 상태 | 협의처 |
| --- | --- | --- |
| 알림 발송 전송 방식 | ✅ **Kafka 확정** (2026-09-11 합의) | — |
| 이벤트에 수신자(`memberId`) 포함 | 🔴 **착수 전 필수** — 현재 `FundingSucceededEvent(fundingId, projectId)`처럼 수신자가 없다. 없으면 알림 대상을 정할 수 없다 | **order·payment 담당자** |
| 이벤트에 `event_id` 포함 | 🔴 **착수 전 필수** — `notifications.event_id`가 NOT NULL이라 없으면 INSERT가 실패해 **알림이 한 건도 안 쌓인다.** 합의가 늦어지면 Kafka 좌표(topic-partition-offset)로 임시 생성할 수는 있으나, 재발행 시 값이 달라져 중복 차단이 되지 않는다 | **order·payment 담당자** |
| Kafka 토픽명·이벤트 스키마 규약 | ⚠️ 미합의 — `@KafkaListener` 어댑터 작성 전에 필요 | order·payment 담당자<br>(이후 project·live·shipping도 같은 규약을 쓴다) |
| `relatedUrl` 경로 규칙 | ⚠️ 위 "알림 문구 예시"의 경로는 **작성자 추측**이다. 스키마에서 `NOT NULL`로 확정했으므로 실제 라우팅과 맞춰야 한다 | **프론트엔드** |
| `notifications.body` 컬럼 삭제 | ⚠️ 알림함이 **제목 한 줄**이라는 전제로 삭제했다. 제목+본문 2줄 레이아웃이면 되살려야 한다 | **프론트엔드 / PM** |
| `live_notify_requests` 소유 서비스 | ⚠️ ERD는 live-service, 이 문서 권고안은 notification-service (NOTI-002 검토의견 참고) | ERD 담당자<br>+ live-service 담당자(미정) |
| `live_id` 타입 (UUID 가정) | ⚠️ ERD 미확인 | ERD 담당자 |
| NOTI-001 (찜 오픈 알림) | ⏸ P1 — member-service가 `wishes`를 훑어 수신자별 이벤트를 발행하는 설계 확정 후 | member-service 담당자 |
| `SELLER_UPDATE_DUE` 발송 | ⏸ shipping-service 미착수 — 유형 정의 + **문구 목업**만(위 "알림 문구 예시" 절). PM 요청으로 배송 UX/UI 발표에 사용 | shipping-service 담당자(미정) |
| `COUPON_EXPIRING` 발송 배치 | ⏸ **중요도 낮음(PM 확인)** — order-service 쿠폰 만료 배치가 생길 때 함께 | order-service 담당자 |
| 웹푸시·이메일·SMS 채널 / `notification_settings.channel` 축 | ⏸ MVP 범위 밖 — 채널 추가 시점에 `channel` 컬럼을 넣는다. API 요청/응답 형태는 그때도 안 바뀐다 | PM (범위 결정) |
| 채널 확장 시 발송 신뢰성 | ⏸ 지금은 사이드이펙트가 **DB 쓰기 하나뿐**이라 `(event_id, member_id)` UNIQUE로 충분하다. 외부 발송(이메일·푸시)이 붙으면 "알림 INSERT 성공 → 발송 전 장애 → 재수신 시 UNIQUE로 스킵 → **발송이 영영 유실**"이 생겨 발송 상태 컬럼 + 재시도 워커가 필요해진다 | 채널 추가 시점 내부 설계 |

> **착수 순서**: 🔴 2건은 order·payment 담당자와 한 번에 정하면 된다(같은 이벤트 계약의 필드 두 개다).
> ⚠️ 프론트 2건은 알림함 화면 설계가 나오는 시점에 맞추면 되고, 그 전까지 구현을 막지는 않는다.
> ⏸ 항목은 해당 서비스가 착수될 때 이 표를 다시 보면 된다.
