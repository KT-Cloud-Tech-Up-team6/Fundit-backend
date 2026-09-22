## LIVE 도메인

> 실행 중인 서비스가 `GET /api/v1/lives/api-docs.yaml`로 OpenAPI 3 문서를 제공합니다.
**구현 현황은 생성 스펙이 기준입니다**
이 문서는 설계 의도와 비즈니스 규칙, 생성 스펙은 실제 구현만 담습니다.
파일로 뽑아 프론트에 전달하는 방법은 `docs/development-workflow-guide.md`의 "API 스펙(OpenAPI) 전달" 참고.
>

### LIVE 도메인 엔드포인트 목록 — 판매자

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/lives` | O (본인 소유 프로젝트) | LIVE 생성 |
| GET | `/api/v1/lives/mine` | O | 내 LIVE 목록 조회(LIVE 홈 탭·임시저장 이어쓰기) |
| PATCH | `/api/v1/lives/{liveId}/settings` | O (본인 소유 LIVE) | LIVE 기본 설정 등록/수정 |
| POST | `/api/v1/lives/{liveId}/cue-sheet` | O (본인 소유 LIVE) | AI 큐시트 생성 요청(비동기) |
| GET | `/api/v1/lives/{liveId}/cue-sheet` | O (본인 소유 LIVE) | AI 큐시트 결과 조회 |
| PATCH | `/api/v1/lives/{liveId}/cue-sheet` | O (본인 소유 LIVE) | AI 큐시트 직접 수정 |
| POST | `/api/v1/lives/{liveId}/start` | O (본인 소유 LIVE) | LIVE 시작 |
| POST | `/api/v1/lives/{liveId}/end` | O (본인 소유 LIVE) | LIVE 종료 |
| GET | `/api/v1/lives/{liveId}/chat/insights` | O (본인 소유 LIVE) | AI 집계 Q&A(FAQ) 조회 |
| GET | `/api/v1/lives/{liveId}/chat/questions/{questionId}` | O (본인 소유 LIVE) | 대표질문(FAQ 클러스터) 원본 채팅 조회 |
| GET | `/api/v1/lives/{liveId}/chat/unanswered` | O (본인 소유 LIVE) | 미답변 질문 창(근거 없음) 조회 |
| POST | `/api/v1/lives/{liveId}/chat/questions/{questionId}/ai-answer` | O (본인 소유 LIVE) | AI 추천답변 초안 생성/등록 |

> `/related-products`(질문 관련 상품정보 단독 조회)는 구현되지 않았다 — AI 실계약(v1)에서 그 역할은
> `ai-answer`(GENERATE)의 `referenceChunks`가 대신한다.
| GET | `/api/v1/lives/{liveId}/highlights` | O (본인 소유 LIVE) | 하이라이트(마커/클립) 목록 조회 |
| PATCH | `/api/v1/lives/{liveId}/highlights/{highlightId}` | O (본인 소유 LIVE) | 하이라이트 구간·라벨·자막 수정 |
| POST | `/api/v1/lives/{liveId}/highlights/{highlightId}/regenerate` | O (본인 소유 LIVE) | 하이라이트 개별 재생성 |
| DELETE | `/api/v1/lives/{liveId}/highlights/{highlightId}` | O (본인 소유 LIVE) | 하이라이트 삭제 |
| PATCH | `/api/v1/lives/{liveId}/highlights/{highlightId}/visibility` | O (본인 소유 LIVE) | 하이라이트 공개 설정 |
| GET | `/api/v1/lives/{liveId}/highlights/stats` | O (본인 소유 LIVE) | 하이라이트 성과 통계 조회 |

### 엔드포인트 목록 — 소비자

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| GET | `/api/v1/lives/banner` | X | 진행중 LIVE 배너 조회 |
| GET | `/api/v1/lives` | X | LIVE 목록 조회(상태별) |
| GET | `/api/v1/lives/{liveId}/playback` | X | LIVE 시청 정보 조회 |
| PUT | `/api/v1/lives/{liveId}/like` | O | LIVE 좋아요(idempotent) |
| DELETE | `/api/v1/lives/{liveId}/like` | O | LIVE 좋아요 취소(idempotent) |
| GET | `/api/v1/lives/{liveId}/share-link` | X | LIVE 공유 링크 생성 |
| GET | `/api/v1/lives/{liveId}/chat/answered-questions` | X | 답변된 질문 모아보기 (채팅창 Q&A 버튼) |
| GET | `/api/v1/lives/{liveId}/vod` | X | 다시보기(VOD) 재생 정보 조회 |
| GET | `/api/v1/lives/{liveId}/vod/chat` | X | 다시보기 시간대별 채팅 조회 |

### 엔드포인트 목록 — 공통 / 내부

| method | path | auth required | 설명 |
| --- | --- | --- | --- |
| POST | `/api/v1/lives/{liveId}/chat/token` | O | IVS Chat 접속 토큰 발급(판매자·소비자 공통) |
| POST | `/internal/v1/lives/chat/messages` | 내부 전용 (Firehose) | 채팅 메시지 적재 — 게이트웨이 경유 아님 |
| GET | `/internal/v1/lives/{liveId}/status` | 내부 전용 (`X-Internal-Api-Key`) | 방송 진행 상태 조회 — order의 라이브 쿠폰 검증용 |

---

### 인증 표기에 관하여

요구사항정의서 2.1.4가 *"회원가입 및 로그인 시 유저는 사용권한을 갖게 되고, **하나의 권한**을 가지고 판매자/구매자 페이지에 접근 가능하다"* 로 정의하고 있고, `accounts.role`도 단일 값이다.
게이트웨이는 JWT를 검증해 `X-User-Id`/`X-User-Roles`만 주입하며, 서비스는 `@LoginUser CurrentUser`로
받는다.

따라서 위 표의 **`O (본인 소유 LIVE)`는 `live_channels.seller_id == 로그인 사용자` 검증**을 뜻한다.
역할 기반으로 읽으면 구현 단계에서 있지도 않은 권한을 찾게 된다.

### 채팅은 IVS Chat에 직접 붙는다

전송 계층이 IVS Chat(관리형)이고, 우리가 중간에 서면 동시 접속만큼의 WS 커넥션을 직접 떠안으면서 얻는 게 없다.

```
클라이언트 ──(IVS Chat WebSocket)──▶ IVS Chat
                                      ├─ 리뷰 핸들러 Lambda : 부적절 메시지 필터링
                                      └─ Chat Logging → Firehose(10초) ──▶ live-service 적재
       ▲
       └── 접속 토큰만 우리 REST로 발급: POST /api/v1/lives/{liveId}/chat/token
```

`CreateChatToken`은 **백엔드만 호출할 수 있다.**
클라이언트가 스스로 토큰을 만들 수 없으므로 발급 지점이 곧 인가 지점이 된다.
게이트웨이가 WS 업그레이드에 헤더를 주입하는지 확인할 필요 자체가 사라진다.

**엔드포인트를 판매자용·소비자용으로 나누지 않는다.**
경로가 둘이면 클라이언트가 자기 역할을 판단해 골라야 하고 그 판단이 틀리면 권한이 어긋난다.
하나의 엔드포인트가 호출자를 보고 정한다.

| 호출자 | 부여 capabilities |
| --- | --- |
| 방송 소유자(`live_channels.seller_id`) | `SEND_MESSAGE`, `DELETE_MESSAGE`, `DISCONNECT_USER` |
| 그 외 로그인 사용자 | `SEND_MESSAGE` |

메시지 **보기 권한은 IVS가 암묵적으로 포함**하므로 따로 부여하지 않는다.

### 적재는 방송 경로 밖에 둔다

클라이언트가 IVS에 직접 붙으면 **우리 서버는 메시지를 보지 못한다.**
그런데 `chat_messages`는 다시보기 시간대별 채팅(요구사항정의서 11.4.4)·대표질문 원본(요구사항정의서 6.4.4.3)·AI 분석 입력으로 계속 필요하다.

리뷰 핸들러 Lambda에서 우리 API를 호출해 적재하는 방법이 구성 요소는 가장 적지만 **쓰지 않는다.**
리뷰 핸들러는 `SendMessage`마다 호출되는 **동기 경로**라, 거기에 우리 DB를 끼우면

- 우리가 느려지면 **시청자 채팅이 그만큼 느려지고**
- 우리가 죽으면 룸에 설정한 `FallbackResult`에 따라 **채팅이 막히거나(DENY) 필터링 없이 통과(ALLOW)** 한다

둘 다 방송을 직접 해친다.
**리뷰 핸들러는 필터링만 하고, 적재는 Chat Logging으로 분리한다.**

전송 대상은 **Kinesis Data Firehose**다.
로깅 전파 지연이 S3는 5분, CloudWatch·Firehose는 10초인데, AI 대표질문 집계가 **3분 주기**(요구사항정의서 6.4.4.3)라 5분 지연으로는 맞출 수 없다.
10초는 다시보기 채팅·AI 집계 양쪽에 충분하다.

### AI 연동 — live가 컨텍스트를 조립해 호출한다 (AI ↔ BE/FE 협의 확정)

AI 추론은 별도 서버(`/api/v1/ai`)가 담당하고, **live-service가 그 앞에 선다.**

```
FE ──▶ live-service ──(서비스 인증 + connect/read 타임아웃)──▶ AI 서버 /api/v1/ai/*
                     ◀──── 결과를 이 문서의 응답 규격으로 변환 ────┘

AI 서버 ──✕──▶ live DB        직접 접근 없음 (커넥션 정보·리드 레플리카 포함)
```

**FE는 AI 서버를 직접 호출하지 않는다.** 흐름은 `FE → BE → AI → BE → FE`로 통일됐다.
FE가 보는 것은 이 문서의 엔드포인트뿐이고, AI 계약 변경이 FE에 새지 않는다.

**AI가 우리 DB를 읽지 않으므로 컨텍스트는 live가 조립해 요청 바디에 싣는다.**
큐시트는 프로젝트 상세·펀딩 스토리(요구사항정의서 6.2.4.2), 코파일럿은 리워드 기본 정보·상세페이지(6.4.4.5),
하이라이트는 VOD·채팅 분류 결과(6.6.3)다.
큐시트·Q&A `prepare`는 **같은 모양의 상품정보**를 쓴다 — 큐시트 요청의 `product`는 `prepare` 본문과 동일하고,
둘 다 `AiProductContextAssembler`가 project-service 공개 상세(`introContent`)·리워드 목록으로 조립한다(큐시트 담당과 합의).
**서비스 핵심 데이터의 정본은 BE가 갖는다.**
AI가 우리 스키마에 의존하기 시작하면 컬럼 하나를 바꿀 때마다 AI 파트와 배포 일정을 맞춰야 한다.

#### 클라이언트 구현

`services/auth-service/.../infrastructure/portone/PortOneRestClient.java` +
`PortOneProperties.java`가 이 레포의 외부 연동 선례다. 새 HTTP 추상화를 만들지 않는다.

- `RestClient` + `SimpleClientHttpRequestFactory`에 **connect/read 타임아웃 명시**
  (루트 `CLAUDE.md`: 서비스 간 동기 호출에 프레임워크 기본값을 그대로 두지 않는다 — `RestClient`
  기본값은 무한 대기라 AI가 느려지면 방송 화면이 같이 멈춘다)
- 호출 실패는 `DependencyFailureException`으로 감싼다(infrastructure 계층, `error-handling.md`)
- base URL·서비스 인증 키는 `@ConfigurationProperties`로 분리한다. 코드에 하드코딩하지 않는다(S7)
- **AI 응답을 신뢰하지 않는다** — 구조·길이·필드 존재를 검증한 뒤 저장한다(S7)

#### AI 에러코드 → 이 서비스의 에러코드

AI가 주는 코드를 그대로 흘려보내지 않는다
`error-handling.md`가 *"등록된 `ErrorCode`가 아닌 임의 문자열 코드를 직접 반환하지 않는다"* 로 정하고 있다.

**Q&A/FAQ(AI팀 실계약 v1, 2026-09-17 확정)** — 아래 2건 외에는 새 `LiveErrorCode`를 만들지 않았다.
`CommonErrorCode`가 이미 409/404를 갖고 있어 재정의할 이유가 없었다.

| AI 응답 | live → FE | 왜 이렇게 매핑하나 |
| --- | --- | --- |
| `409 NOT_PREPARED` (`submitComments`) | `CommonErrorCode.CONFLICT` (409) | 상품정보 색인(`prepare`) 누락 — 우리 쪽 배선 실수라 사용자에게 보일 값은 아니지만 로그에서 원인을 바로 구분하려고 409로 둔다 |
| `404` (`unansweredDetail`/`registerSellerAnswer`) | `CommonErrorCode.NOT_FOUND` (404) | 클러스터(`qid`)가 이미 사라짐(재클러스터링 등) |
| `401 UNAUTHORIZED` 등 그 외 전부 | `DependencyFailureException` (503) | 우리 쪽 토큰 설정 오류나 AI 서버 장애 — 사용자 응답이 아니라 장애로 다룬다 |

**큐시트·하이라이트(미확정, 초안 상태 유지)** — 아래는 AI팀과 아직 확인되지 않은 가정이다. 확인되면 이 표를 고친다.

| AI 응답 | live → FE | 왜 이렇게 매핑하나 |
| --- | --- | --- |
| `409 NOT_READY_TO_GENERATE` | `LiveErrorCode.CUE_SHEET_NOT_READY` (409) | `CommonErrorCode`에 409가 `CONFLICT` 하나뿐이라 위 Q&A 항목과 합쳐진다. FE가 "생성 조건 미충족"과 "AI 준비 중"을 같은 문구로 띄우면 안 된다 |
| `422 VALIDATION_ERROR` | `CommonErrorCode.BUSINESS_RULE_VIOLATION` (422) | **우리가 조립한 입력이 규격을 벗어난 것 = 우리 버그**다. 사용자가 고칠 수 없으니 도메인 코드를 따로 만들 이유가 없다 |
| `502 GENERATION_FAILED` | `LiveErrorCode.AI_GENERATION_FAILED` (503) | `CommonErrorCode`에 502가 없다. 재시도하면 될 수 있는 생성 실패와 AI 서버 다운(`DEPENDENCY_FAILURE`)을 FE가 구분해야 한다 |

**`EVIDENCE_UNAVAILABLE`(근거 없음)은 에러가 아니다.**
코파일럿은 판매자가 초안을 보고 판단하는 화면이다.
503을 던지면 화면에 보여줄 게 없어지는데, 요구사항정의서 6.4.4.5는 *"관련 상품정보가 없습니다"* Empty State를 요구한다. **근거 없음은 정상 응답의 한 상태다.**
실계약의 `GET /unanswered/{qid}`(추천답변 초안 경로)에는 grounded 플래그가 없다 — 근거를 못 찾으면 `reference.chunks`가 빈 배열로 온다. 그래서 `AiAnswerResponse.referenceChunks`가 비어 있는 것이 곧 Empty State 신호다. (`AiClient.Grounding`은 채팅 배치 응답 `submitComments` 쪽에만 있는 값이고, 값은 `GROUNDED`/`PARTIAL_GROUNDED`/`SELLER_CONFIRMED`다.)

#### 비동기 생성 — 폴링 대상은 우리다 (큐시트·하이라이트만 해당)

큐시트(`202 Accepted` → `GET /cue-sheet`의 `status`)와 하이라이트(`generation_status`)가 이 방식이다.
**FE는 AI를 폴링하지 않고 live를 폴링한다.**

> ⚠️ **큐시트·하이라이트에서 live가 AI 결과를 회수하는 경로는 아직 미정이다** — 위 표와 별개로
> 여전히 초안이다. live가 AI를 폴링하는지, AI가 결과를 우리 내부 엔드포인트로 밀어주는지에 따라
> AI job 식별자 컬럼 필요 여부가 갈린다.
> 큐시트는 **BE가 AI를 호출하고 응답으로 `segments`를 받는 동기 방식**을 큐시트 담당에게 제안해 회신 대기 중이다
> (경로·최대 생성 시간·Q&A와 같은 서버인지). 확정 전까지 콜백 엔드포인트는 유지한다.
>
> **Q&A/FAQ는 이 문제가 없다** — 애초에 비동기 결과가 없다. BE가 채팅 배치를 넘기면 그 HTTP
> 응답으로 바로 답변이 오고(`submitComments`), 나머지(`faq`/`unanswered`/`faqComments`)는
> BE가 화면을 그릴 때마다 동기 조회한다(AI팀 실계약 v1, 2026-09-17 확정).

**AI 장애가 방송을 막지 않는다.**
큐시트·대표질문·하이라이트 호출이 전부 실패해도 송출과 채팅은 정상이어야 한다(요구사항정의서 6.4.4.2).
AI 호출을 방송 시작·채팅 전송 경로의 동기 의존으로 두지 않는다 — Q&A/FAQ 쪽은
`ChatCommentBatchSender`(3초 주기 배치)와 `prepare`/`updateContext`(트랜잭션 커밋 후 호출)가
이 원칙을 지킨다.

---

### ⚠️ 적재 엔드포인트는 기존 내부 인증 방식이 통하지 않는다

```
POST /internal/v1/lives/chat/messages
```

`modules:common-webmvc`의 `InternalGatewaySecretFilter`는 **게이트웨이가 주입한`X-Internal-Api-Key`** 를 전제한다.
Firehose는 게이트웨이를 거치지 않으므로 이 필터를 그대로 적용하면 **전량 401이 난다.**

→ Firehose HTTP 엔드포인트의 access key 헤더를 쓰는 별도 인증 경로를 두고, 이 경로는 게이트웨이 라우팅에서 제외한다(외부 노출 차단).

---

## 상세 명세 (판매자)

### LIVE 생성 (요구사항정의서 6.1.4)

```
POST /api/v1/lives
```

Auth Required: **O** (본인 소유 프로젝트)

Request Body

```json
{ "projectId": "0198f2b1-7c3d-7xxx-xxxx-xxxxxxxxxxxx" }
```

Response Body

```json
{ "liveId": "0199c3a0-1b2c-7xxx-xxxx-xxxxxxxxxxxx", "status": "DRAFT" }
```

Validation / Business Rules

- `projectId`는 **project-service의 `public_id`(UUID)** 다. live-service는 내부적으로 BIGINT `id`로 변환해 저장한다 — 초안 스펙은 예시가 UUID인데 ERD 컬럼이 BIGINT라 타입이 어긋나 있었다.
- 본인 소유 프로젝트가 아니면 `403`. 존재하지 않으면 `404`.
- 생성 직후 상태는 **`DRAFT`** 다. 방송 예정일시는 다음 단계(요구사항정의서 6.2.4.1)에서 받으므로, 이 시점에 `SCHEDULED`로 두면 예정 시각 없는 예약 상태가 된다.
- 채널(`live_channels`)이 없으면 함께 프로비저닝한다. **판매자당 채널 1개**이므로 동시에 두 방송을 송출할 수 없다 — 요구사항정의서 6.1.3의 "동일 프로젝트 반복 LIVE 개설"은 순차 진행이라 이 제약으로 충분하다.
- `liveId`는 `live_sessions.public_id`이며 이후 모든 소비자 노출 경로(`/live/{liveId}`)에서 그대로 쓴다.

---

### 내 LIVE 목록 조회 (요구사항정의서 6.1.3)

```
GET /api/v1/lives/mine?status=DRAFT&page=0&size=20
```

Auth Required: **O**

Response Body

```json
{
  "content": [
    { "liveId": "0199...", "introText": "...", "status": "DRAFT", "projectId": "0199...",
      "scheduledStartAt": null, "createdAt": "2026-09-10T18:00:00+09:00" }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

Validation / Business Rules

- 요구사항정의서 6.1.3의 *"여러 LIVE 진행 시 LIVE 홈 탭[FL_S_LV_HOME]에 목록으로 표출"* 에 대응한다.
- **소비자 목록(`GET /api/v1/lives`)으로 대체할 수 없다.** 그쪽은 비인증이고 `DRAFT`를 제외하므로,
  임시저장한 LIVE(6.2.4.1의 "임시저장 후 이어서 작성")로 돌아갈 경로가 없어진다.
- 본인이 만든 세션만 내려준다 — `sellerId`를 `@LoginUser`로 고정한다(S4). `status`는 선택 필터다.
- 정렬은 생성 최신순이다.

---

### LIVE 기본 설정 등록/수정 (요구사항정의서 6.2.4.1)

```
PATCH /api/v1/lives/{liveId}/settings
```

Auth Required: **O** (본인 소유 LIVE)

Request Body

```json
{
  "scheduledStartAt": "2026-09-10T20:00:00+09:00",
  "category": { "major": "테크·가전", "minor": "생활가전" },
  "introText": "5분만에 알아보는 신제품 미니 가습기"
}
```

Response Body

```json
{ "liveId": "0199c3a0-...", "status": "SCHEDULED", "scheduledStartAt": "2026-09-10T20:00:00+09:00" }
```

Validation / Business Rules

- 부분 업데이트다. 카테고리·소개문구는 연결된 프로젝트 값이 기본으로 채워지고 이 API로 덮어쓴다.
- **`scheduledStartAt`이 채워지면 상태가 `DRAFT` → `SCHEDULED`로 올라간다.** 예약 없이 바로 시작하는 경우 이 필드는 비워둔 채 "LIVE 시작"로 간다.
- `introText`는 200자 제한. 소비자 화면에 그대로 노출되므로 출력 인코딩 대상이다(`security.md` S2).
- **연결된 프로젝트·상품 정보는 이 API로 바꿀 수 없다**(요구사항정의서 6.2.4.1 "변경 불가"). 프로젝트를 바꾸려면 LIVE를 새로 만든다.
- 이미 `LIVE`·`ENDED` 상태면 `409`.

---

### AI 큐시트 생성/조회/수정 (요구사항정의서 6.2.4.2)

```
POST /api/v1/lives/{liveId}/cue-sheet
```

Auth Required: **O** (본인 소유 LIVE)

Request Body

```json
{
  "mode": "SCENARIO",
  "targetDurationSec": 580,
  "demoAvailable": true,
  "emphasisPoints": ["10년 무상 A/S"],
  "tone": "ACTIVE",
  "mandatoryPhrases": ["환불 규정은 상세페이지 참고"]
}
```

Response Body (202 Accepted)

```json
{ "status": "GENERATING" }
```

```
GET /api/v1/lives/{liveId}/cue-sheet
```

Response Body

```json
{
  "status": "COMPLETED",
  "mode": "SCENARIO",
  "totalDurationSec": 580,
  "segments": [
    { "id": "scene-1", "title": "오프닝", "duration": 60, "outline": "제품명·핵심 한 줄 소개", "script": null }
  ]
}
```

```
PATCH /api/v1/lives/{liveId}/cue-sheet
```

Request Body

```json
{ "segments": [ { "id": "scene-1", "title": "오프닝", "duration": 45, "outline": "수정된 내용", "script": null } ] }
```

Validation / Business Rules

- `mode`: `SCENARIO`(진행 순서 + 구간별 주요 내용 + 예상 시간 + 핵심 포인트) 또는 `SCRIPT`(위 전부 + 구간별 완성 대사). `SCENARIO`면 `script`는 `null`이다.
- **`targetDurationSec`은 600(10분) 이하**여야 한다(요구사항정의서 6.2.3). 초과 시 `400`.
- 이미 `GENERATING` 상태면 `409` — 중복 생성 요청을 막는다(요구사항정의서 6.2.4.2).
- **`jobId`를 따로 발급하지 않는다.** 세션당 큐시트가 1개라 `GET /cue-sheet`의 `status`로 폴링하면 충분하다. 초안의 `jobId`는 조회 경로가 별도로 없어 쓸 데가 없었다.
- 재생성은 기존 행을 덮어쓴다. 이력 보관 요구가 없다.
- `PATCH`는 판매자 직접 수정이며, 구간 추가·순서 변경도 이 API로 한다. 본문은 `{ "segments": [...] }`이고 **비어 있지 않은 JSON 배열**이어야 한다(아니면 `400`). 구간 내부는 서버가 해석하지 않고 그대로 저장한다. 필드명은 FE `CueScene`에 맞춘 `id`/`title`/`duration`(초)/`outline`/`script`이고(큐시트 담당 제안), AI가 근거 없는 수치를 경고할 때는 **구간 안에** `warnings: [{ "field", "reason" }]`를 싣는다 — 배열 밖 최상위 필드는 저장되지 않는다.
- `mode`는 `SCENARIO`/`SCRIPT`만 받는다. 생성 결과 콜백의 `status`는 `COMPLETED`/`FAILED`만 받으며, **모르는 값을 실패로 굳히지 않고 `400`으로 돌려보낸다** — `PROCESSING` 같은 값을 FAILED로 저장하면 되돌릴 경로가 없다.
- ⚠️ **AI 결과 콜백(`POST /internal/v1/lives/{liveId}/cue-sheet`)의 `segments`는 문자열이 아니라 JSON 배열이다.** 문자열로 받던 때는 JSON이 아닌 값이 JSONB 컬럼까지 가서 `400`이 아니라 `500`이 났다. 요청·응답·콜백 세 경로가 같은 모양(배열)이 됐다.
- **생성 요청이 동시에 들어오면 한 건만 통과한다.** 세션 행을 잠근다 — 잠그지 않으면 더블클릭한 두 요청이 둘 다 "생성 중 아님"을 보고 AI 작업이 두 번 돈다.
- AI 응답은 그대로 신뢰하지 않고 구조·길이를 검증한 뒤 저장한다(`security.md` S7).

---

### LIVE 시작 / 종료 (요구사항정의서 6.3.4)

```
POST /api/v1/lives/{liveId}/start
```

Response Body

```json
{ "liveId": "0199c3a0-...", "status": "LIVE", "ingestEndpoint": "rtmps://xxx.global-contribute.live-video.net:443/app/", "startedAt": "2026-09-10T20:00:03+09:00" }
```

```
POST /api/v1/lives/{liveId}/end
```

Response Body

```json
{ "liveId": "0199c3a0-...", "status": "ENDED", "endedAt": "2026-09-10T20:09:41+09:00" }
```

Validation / Business Rules

- 시작은 `DRAFT`·`SCHEDULED`에서만 가능하다. 종료는 `LIVE`에서만 가능하다. 그 외는 `409`.
- **스트림 키는 응답에 담지 않는다.** 송출 소프트웨어 설정용 키는 별도 발급 경로로 분리하고, 여기서는 `ingestEndpoint`만 돌려준다 — 방송 시작 응답은 로그·브라우저 히스토리에 남기 쉬운 값이다.
- 송출 오류 시 상태를 `ERROR`로 두고 `error_detail`을 함께 저장한다(요구사항정의서 6.3.4). 응답은 사유를 일반화해 내보낸다(S10).
- **종료 시 `live.ended.v1` 이벤트를 발행한다.** 이 이벤트가 AI 질문요약 생성(요구사항정의서 6.5.4.1)과 하이라이트 자동 생성(요구사항정의서 6.6.4)의 트리거다.
- 질문요약이 완성되면 **`live.questions-summarized.v1`을 추가로 발행**해 project-service가 LIVE 검증 탭을 채우게 한다(아래 "질문요약 발행" 절).
- **시작 시 `live.started.v1`을 발행**한다. `notification.raised.v1`을 직접 쏘지 않는 이유: 그 토픽은
  수신자(`memberId`)가 채워져 있어야 하는데 신청자 목록(`live_notify_requests`)은 notification이 소유한다.
  live는 누구에게 보낼지 알 방법이 없다. notification이 이 도메인 이벤트를 구독해 알림을 만든다.

---

### 채팅 접속 토큰 발급 (요구사항정의서 6.4.4.1)

```
POST /api/v1/lives/{liveId}/chat/token
```

Auth Required: **O** (로그인). 판매자·소비자 공통이다.

Request Body: 없음

Response Body

```json
{
  "token": "AQICAHj...",
  "sessionExpirationTime": "2026-09-10T21:00:00+09:00",
  "tokenExpirationTime": "2026-09-10T20:01:00+09:00",
  "roomArn": "arn:aws:ivschat:ap-northeast-2:123456789012:room/abc123",
  "capabilities": ["SEND_MESSAGE"]
}
```

Validation / Business Rules

- 서버가 `CreateChatToken`을 호출해 발급한다. **클라이언트는 토큰을 직접 만들 수 없다** — 발급 지점이 곧 인가 지점이다.
- **capabilities는 호출자를 보고 서버가 정한다. 요청 본문으로 받지 않는다.**
    - 방송 소유자: `SEND_MESSAGE`, `DELETE_MESSAGE`, `DISCONNECT_USER`
    - 그 외 로그인 사용자: `SEND_MESSAGE`
    - 보기 권한은 IVS가 암묵적으로 포함하므로 부여하지 않는다
- 진행 중(`LIVE`)이 아닌 방송에는 발급하지 않는다 — `409`.
- 표시명·프로필 등은 토큰 `attributes`에 실어 매 메시지에 함께 전달한다. **클라이언트가 보낸 표시명을 그대로 싣지 않는다** — 타인 사칭이 가능해진다.
- 세션 수명(`sessionDurationInMinutes`)은 방송 길이(10분 이내)를 고려해 짧게 잡고, 만료 시 재발급한다.
- AI 추천답변 전송(요구사항정의서 6.4.4.6)도 서버가 이 경로로 얻은 토큰으로 보낸다 — 별도 전송 경로를 만들지 않는다.

> 판매자 LIVE 송출 화면의 실시간 채팅 표시는 이 토큰으로 IVS Chat에 직접 구독해서 해결된다.
>

---

### 채팅 메시지 적재 (요구사항정의서 11.3.4, 내부 전용)

```
POST /internal/v1/lives/chat/messages
```

Auth Required: **내부 전용** — Firehose access key 헤더. 게이트웨이를 경유하지 않는다.

Request Body (Firehose 배치)

```json
{
  "records": [
    { "roomArn": "arn:aws:ivschat:...:room/abc123", "messageId": "AYk6xK...", "senderId": "0199...",
      "content": "사이즈가 어떻게 되나요?", "sentAt": "2026-09-10T20:03:11Z" }
  ]
}
```

Validation / Business Rules

- **Chat Logging → Firehose 경로로 들어온다.** 전파 지연 약 10초.
- `roomArn`으로 `live_sessions.ivs_chat_room_arn`을 찾아 적재한다(조인 없는 단일 조회). 모르는 룸이면 그 레코드만 건너뛴다.
- **`messageId` 기준으로 멱등 처리한다** — `ON CONFLICT DO NOTHING`. Firehose는 재전송이 가능해서 이 제약이 없으면 같은 메시지가 여러 번 쌓인다.
- 한 레코드가 실패해도 배치 전체를 실패시키지 않는다.
- **이 엔드포인트를 게이트웨이 라우팅에 노출하지 말 것** — 노출되면 외부에서 임의 채팅을 주입할 수 있다.

---

### AI 집계 Q&A(FAQ) 조회 (요구사항정의서 6.4.4.2, AI 실계약 v1)

```
GET /api/v1/lives/{liveId}/chat/insights?topN=10
```

**집계는 AI가 한다 — BE는 여기서 topic으로 다시 GROUP BY하지 않는다.** `aiClient.faq()` 응답을
로컬 `live_question_summaries`에 upsert(고정된 `questionId`를 유지하기 위해서일 뿐)하고 그대로 내려준다.

Response Body

```json
{
  "qna": [
    { "questionId": "0199d1...", "summaryText": "타이머 기능 돼요?", "count": 4,
      "category": "앱·원격제어", "answeredBy": "SELLER", "answeredAt": "2026-09-20T20:06:00Z",
      "answerText": "네, 최대 12시간입니다.", "promoted": true }
  ]
}
```

Validation / Business Rules

- 정렬은 **AI가 이미 확정해 내려준 순서 그대로**다(요구사항정의서 6.4.4.3, 질문 발생 건수 내림차순).
- 집계 데이터가 없으면 `qna: []`다. 클라이언트가 Empty State를 표시한다.
- **이 API가 실패해도 LIVE 방송·채팅은 정상 동작해야 한다**(요구사항정의서 6.4.4.2) — AI 실패는
  `DependencyFailureException`(503)으로 뜨고, 호출 실패를 방송 화면 전체의 오류로 처리하지 않는다.
- `aiStatus: PREPARING` 같은 별도 상태 필드는 **없다.** `prepare` 미호출 상태에서 채팅 배치를
  보내면 `409`가 나지만, 조회 계열(`faq`/`unanswered`)은 AI가 빈 결과로 응답하는 것으로 확인됐다
  (2026-09-17 E2E 검증).

---

### 대표질문(FAQ 클러스터) 원본 채팅 조회

```
GET /api/v1/lives/{liveId}/chat/questions/{questionId}
```

**원본은 이제 로컬에 없다.** `chat_messages.question_summary_id`로 찾던 이전 설계를 걷어냈다 —
AI의 `GET /faq/{qid}/comments`가 클러스터 원본을 그대로 갖고 있어 그쪽에 위임한다
(`questionId`는 우리 로컬 PK, 내부적으로 `live_question_summaries.ai_question_id`(AI의 `qid`)로
변환해 AI를 호출한다).

Response Body — 배열을 그대로 반환한다(래퍼 없음)

```json
[ { "commentId": "c2", "content": "예약 타이머 있어요?", "atMs": 20000 } ]
```

Validation / Business Rules

- 본인 LIVE 소유의 `questionId`만 접근 가능하다(S4) — 세션 소속을 대조하지 않으면 남의 liveId에
  아무 `questionId`나 붙여 원본을 읽을 수 있다(IDOR).
- 출력 시 인코딩한다(S2).

---

### 미답변 질문 창 조회 (요구사항정의서 6.4.4.5)

```
GET /api/v1/lives/{liveId}/chat/unanswered?topN=10
```

AI가 근거를 못 찾은(`UNANSWERABLE`) 질문만 모인다. `pending`은 아직 판매자 답변 전, `answered`는
이미 등록된 답변이 있어 화면에서 회색 처리할 항목이다.

Response Body

```json
{
  "pending": [ { "questionId": "0199d1...", "representativeText": "타이머 기능 돼요?", "count": 3 } ],
  "answered": [ { "questionId": "0199d2...", "representativeText": "판매자 답변 완료 건", "count": 2 } ]
}
```

---

### AI 추천답변 초안/등록 (요구사항정의서 6.4.4.6)

```
POST /api/v1/lives/{liveId}/chat/questions/{questionId}/ai-answer
```

**미답변 창(`GET /chat/unanswered`)에서 진입한 질문 전용이다** — 근거를 찾은 질문은 채팅 배치
응답으로 이미 즉시 답변되어 있어 이 흐름을 타지 않는다.

Request Body — 초안 미리보기(`GENERATE`, 아무것도 기록하지 않는다)

```json
{ "action": "GENERATE" }
```

Response Body

```json
{ "draftAnswer": "500ml/700ml 두 가지 사이즈로 제공됩니다.", "referenceChunks": ["..."], "sent": false }
```

Request Body — 등록(`SEND`)

```json
{ "action": "SEND", "finalAnswer": "500ml/700ml 두 가지 사이즈로 제공됩니다." }
```

Validation / Business Rules

- **`GENERATE`는 초안만 만든다. `SEND`를 호출해야 AI의 `registerSellerAnswer`에 등록되고
  `live_question_summaries.answer_text`/`is_answered`가 채워진다** — 자동 게시가 아니다(요구사항정의서 6.4.3).
- **채팅에는 아직 자동 게시되지 않는다.** `IvsClient`에 `SendMessage`류가 없어(스텁만 존재)
  실제 채팅 게시는 그 클라이언트가 생기는 별도 작업으로 미뤄졌다 — 지금은 저장·조회까지만이다.
- `referenceChunks`는 근거가 아니라 판매자 참고용이다. AI가 확인 못 한 사실은 `draftAnswer`에
  `[판매자 확인 필요: ...]`로 비워둔다(임의 생성 금지).
- **환불·결제·배송 등 정책 항목은 AI가 요약·재구성하지 않고 판매자가 등록한 원문을 그대로 제공한다**(요구사항정의서 6.4.3).
- 등록되면 AI의 Live Knowledge에도 반영돼(`SellerAnswerResult.liveKnowledgeRegistered`) 이후
  같은/유사 질문은 LLM 없이 이 답변으로 즉시 응답된다(`SELLER_CONFIRMED`).
- 전송 전 출력 인코딩·필터링을 거친다(S2). 생성 실패 시 수동 답변으로 유도한다.

---

### 질문요약 발행 (요구사항정의서 6.5.4.1) — Kafka

방송 종료 후 AI가 만든 대표질문을 **project-service에 Kafka로 밀어준다**(폴링 아님, 담당자 협의 확정).
project의 `live_verifications.question_count` 주석에 있던 *"live-service 연동 전까지는 0"* 이 이걸로 채워진다.

| 토픽 | 발행 | 구독 | 파티션 키 |
| --- | --- | --- | --- |
| `live.ended.v1` | live | AI 파트(질문요약·하이라이트 생성 트리거) | `liveId` |
| `live.questions-summarized.v1` | live | project (LIVE 검증 탭) | `liveId` |

payload — 봉투 없이 평평한 JSON(`event-convention.md` 4번)

```json
{
  "eventId": "live:1042",
  "liveId": "0199c3a0-...",
  "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",
  "summaries": [
    { "questionSummaryId": "0199d1...", "summaryText": "배송은 얼마나 걸리나요?", "questionCount": 12 }
  ]
}
```

Validation / Business Rules

- **아웃박스(`live_event_outbox`)로 발행한다.** 이 이벤트가 유실되면 LIVE 검증 탭이 영영 비어 있고, 방송은 이미 끝나서 재생성 트리거가 없다. `eventId`는 `"live:{outboxId}"`(규약 5번).
- `projectId`를 실어 보낸다 — project가 `live_verifications.project_id`를 채울 때 live에 되묻지 않아도 된다.
  ⚠️ **현재 구현은 project-service의 `public_id`(UUID, 문자열)를 그대로 싣는다.**
  `live_verifications.project_id`가 `projects(id)`(내부 BIGINT) FK라 그대로는 못 쓸 수 있다 —
  live-service는 project-service 공개 API로 내부 BIGINT를 받을 방법이 없어(공개 상세 응답에
  UUID만 있음), 필요하면 project 담당자가 UUID→BIGINT 변환을 컨슈머 쪽에서 하거나 별도 조회
  API를 열어야 한다(확인 대기).
- `questionSummaryId`는 `live_question_summaries.public_id`이고, project의 `question_summary_id`(VARCHAR(100))와 타입이 맞는다.
- 소비 측 멱등 기준(`eventId` vs `questionSummaryId` UNIQUE)은 project 담당자와 맞춘다 — Kafka는 at-least-once라 같은 이벤트가 두 번 온다.
- 두 토픽을 `modules:common`의 `KafkaTopics`와 `.claude/rules/event-convention.md` 토픽 표에 등록해야 한다(이미 등록되어 있다면 재확인).
- **project-service 쪽 컨슈머는 아직 없다** — 이번 작업은 발행까지만 완성했다(담당자가 다름).

---

### LIVE 검증 탭 — 이 서비스에 엔드포인트를 두지 않는다

project-service가 `live_verifications` 테이블을 이미 갖고 있고, 그 마이그레이션 주석이 경계를 정해뒀다
*"방송 송출 자체는 live-service 소관이고, project-service는 방송 종료 후 남는 질문요약 참조값(`question_summary_id`, FK 아님)과 판매자 답변만 보관한다."*

|  | 소유 | 담는 것 |
| --- | --- | --- |
| live-service | `live_question_summaries` | AI 대표질문·발생 건수·답변 여부 |
| project-service | `live_verifications` | 위 질문요약의 참조값 + **판매자 답변** |

**판매자 답변 등록·수정·삭제 API는 project-service 소관이다.**
소비자 LIVE 검증 탭을 그리는 쪽도 project라, 여기에 같은 걸 또 두면 답변이 두 군데 생기고
어느 쪽이 정본인지 알 수 없게 된다.

live-service는 **질문요약을 만들어 제공하는 데까지**(요구사항정의서 6.5.4.1) 맡는다.
project-service는`live_question_summaries.public_id`를 `question_summary_id`로 참조한다.

> **전달 방식은 Kafka로 확정됐다**(담당자 협의).
project의 `live_verifications.question_count`가 *"live-service 연동 전까지는 0"* 인데, live가 `live.questions-summarized.v1`을 발행하면 그걸로 채워진다.
폴링은 없다 — 상세는 위 "질문요약 발행 (요구사항정의서 6.5.4.1)" 절 참고.
>

---

### 하이라이트 조회/수정/재생성/삭제/공개설정/통계 (요구사항정의서 6.6.4)

```
GET /api/v1/lives/{liveId}/highlights
```

Response Body

```json
{
  "markers": [
    { "highlightId": "0199e1...", "startSec": 320, "sceneLabel": "DEMO", "title": "실시간 시연", "public": false }
  ],
  "clips": [
    { "highlightId": "0199e2...", "startSec": 300, "endSec": 380, "sceneLabel": "PRICE_BENEFIT",
      "clipUrl": "<https://cdn>.../clip1.mp4", "caption": "런칭 특가 안내", "public": false, "generationStatus": "COMPLETED" }
  ]
}
```

```
PATCH   /api/v1/lives/{liveId}/highlights/{highlightId}            // 구간·라벨·자막 수정
POST    /api/v1/lives/{liveId}/highlights/{highlightId}/regenerate // 개별 재생성
DELETE  /api/v1/lives/{liveId}/highlights/{highlightId}            // 삭제
PATCH   /api/v1/lives/{liveId}/highlights/{highlightId}/visibility // { "public": true }
GET     /api/v1/lives/{liveId}/highlights/stats
```

Response Body — 통계

```json
{ "items": [ { "highlightId": "0199e2...", "viewCount": 1200, "clickCount": 85 } ] }
```

> ⚠️ **이 엔드포인트는 P2이고 집계 주체가 미정이다.** 세 수치 모두 지금 설계로는 채워지지 않는다
소비자 하이라이트 노출을 project-service LIVE 검증 탭에 넘겼으므로 조회·클릭이 우리를 거치지 않고,
펀딩 전환 기여는 order 집계와 대조해야 한다.
**집계 주체(project/order/데이터팀)를 정한 뒤 구현한다.**
>

```json
{ "note": "집계 주체 확정 전까지 구현 보류" }
```

Validation / Business Rules

- **마커와 클립은 한 테이블(`live_highlights`)에 `kind`로 구분해 저장한다.** 응답만 두 배열로 나눠 내려준다 — 컬럼이 거의 같아 테이블을 쪼개면 수정·재생성·공개설정 API가 전부 두 벌이 된다.
- `sceneLabel`: `DEMO`(실시간 시연) / `AUDIENCE_REACTION`(시청자 반응 집중) / `SPEC`(핵심 스펙 설명) / `PRICE_BENEFIT`(가격·혜택 안내) / `COMPARISON`(비교 설명).
- **자동 생성 결과는 `public: false`로 시작한다.** 판매자가 확정하기 전까지 소비자 화면에 나오지 않는다(요구사항정의서 6.6.3).
- **방송 1회당 클립은 최대 3개, 길이 60~120초**다(요구사항정의서 6.6.3).
- **동시에 들어온 콜백도 상한을 넘기지 못한다.** 세션 행을 잠그고 센다 — 콜백은 at-least-once라 잠그지 않으면 둘 다 "0개"를 읽고 각각 3개를 넣는다.
- **`endSec`은 `kind`가 정한다** — `CLIP`은 필수, `MARKER`는 둘 수 없다. DDL의 `end_sec`은 NULL 허용이라 DB가 막아주지 않고, 끝 없는 클립은 플레이어가 구간을 잡지 못한다.
- **AI가 상한을 넘겨 보내면 초과분만 건너뛰고 앞의 것은 저장한다.** 예외로 막으면 한 트랜잭션이라 성공분까지 롤백돼 아래 "일부만 실패해도 성공분은 노출한다"와 정면으로 충돌한다. 건너뛴 개수는 로그에만 남는다.
- 일부 클립만 실패해도 성공한 마커·클립은 정상 노출한다. 실패분은 `generationStatus: FAILED`로 내려 재생성을 유도한다.
- **재생성 결과 콜백은 대상 `highlightId`를 그대로 실어 보내야 한다.** 없으면 새 행이 생겨 원래 행이 `GENERATING`으로 영영 남고, 클립 수가 늘어 3개 상한에 걸려 재생성 자체가 막힌다. 최초 생성 콜백은 `highlightId`를 비워 보낸다.
- `kind`·`sceneLabel`·`status`는 정해진 값만 받는다. 모르는 값은 `400`이다 — DB 제약까지 가면 `500`으로 보인다(S2).
- 다시보기가 저장되지 않았거나 영상이 손상된 경우 생성 불가를 안내한다.
- 자막은 출력 인코딩 대상이다(S2).

---

### 라이브 쿠폰 (요구사항정의서 16.6.4) — order-service에 위임한다

**이 서비스에 쿠폰 엔드포인트와 테이블을 두지 않는다.**
order-service가 쿠폰 생애주기 전체 (발급·클레임·사용 처리·복원·정산 차감)를 이미 갖고 있고,
LIVE 채널용 컬럼도 들어가 있다.

```sql
-- order-service coupons
issue_channel   ('GENERAL' | 'LIVE')
live_session_id  BIGINT          -- live-service 참조
drop_type       ('FIRST_COME' | 'MANUAL_DROP' | 'WATCH_TIME_AUTO')
version          INT             -- 낙관적 락(재고 차감)
```

| 기능 | 호출할 API |
| --- | --- |
| 판매자 쿠폰 생성·드롭 | `POST /api/v1/sellers/coupons` + `issueChannel: "LIVE"`, `liveSessionId`, `dropType` |
| 소비자 클레임 | `POST /api/v1/coupons/{couponCode}/claim` |
| 발급·전환 통계 | order 조회 API (live 화면에서 보여줄 뿐) |

**소비자 클레임을 live가 감싸지 않는다.**
클라이언트가 order를 직접 부른다
live가 한 번 더 감싸면 재고 차감 경로가 두 개가 되고, 그 순간 낙관적 락이 지켜주던 정합성이 깨진다.

---

### 방송 진행 상태 조회 (내부 전용)

```
GET /internal/v1/lives/{liveId}/status
```

Auth Required: **내부 전용** — `X-Internal-Api-Key`. 게이트웨이 라우팅에서 제외한다.

Response Body

```json
{ "liveId": "0199c3a0-...", "sessionId": 42, "status": "LIVE", "sellerId": "0199a1b2-..." }
```

Validation / Business Rules

- **order가 라이브 쿠폰 발급·클레임 시 "방송 진행 중"을 확인하는 용도다**(요구사항정의서 16.6.3 — *"LIVE 방송 진행 중에만 발급 가능"*, *"미시청자에게는 노출·발급되지 않음"*). 이 검증이 없으면 방송을 안 본 사람도 라이브 쿠폰을 받는다.
- **호출 방향이 order → live다.** 쿠폰의 주인이 order이므로 판정에 필요한 정보를 order가 가져간다. 반대로 live가 order 쿠폰 API를 감싸면 위임이 아니라 이중 관리가 된다.
- 응답은 판정에 필요한 최소값만 담는다. 방송 제목·썸네일 같은 건 넣지 않는다 — 내부 API가 화면용 데이터를 실어 나르기 시작하면 공개 API와 구분이 사라진다.
- `sellerId`를 함께 주는 이유: order가 "쿠폰을 발행한 메이커가 이 방송의 주인인가"를 확인할 수 있어야 한다.

> ⚠️ **order 담당자 확인 필요**: 현재 `MakerCouponIssueService`가 `IssueChannel.GENERAL`을 하드코딩하고 있어, 그 API로는 LIVE 채널 쿠폰을 만들 수 없다. `issueChannel`·`liveSessionId`·`dropType`을 요청으로 받도록 확장이 필요하다.
>

---

## 상세 명세 (소비자)

### 진행중 LIVE 배너 / LIVE 목록 조회 (요구사항정의서 10.1.4 / 11.1.4)

```
GET /api/v1/lives/banner
```

Response Body

```json
{ "items": [ { "liveId": "0199...", "introText": "...", "thumbnailUrl": "...", "viewerCount": 234 } ] }
```

```
GET /api/v1/lives?status=LIVE&page=0&size=20
```

Response Body

```json
{
  "content": [
    { "liveId": "0199...", "introText": "...", "status": "LIVE", "viewerCount": 234, "thumbnailUrl": "...",
      "scheduledStartAt": "2026-09-10T20:00:00+09:00", "likeCount": 128 }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

Validation / Business Rules

- **응답에 `title`이 없다.** 요구사항정의서 6.2.4.1의 LIVE 입력 항목은 카테고리·소개 문구·방송 예정일뿐이고
  LIVE 제목 입력이 없다. 목록 카드에 노출할 문구는 `introText`(소개 문구)다.
- `status`: `SCHEDULED`(오픈 예정) / `LIVE`(진행 중) / `ENDED`(다시보기). 생략 시 전체.
- **`DRAFT` 상태는 소비자 목록에 절대 나오지 않는다** — 설정이 끝나지 않은 방송이다.
- 인증 불필요. 조회 조건은 바인딩 변수로 처리한다(S1).
- **`viewerCount`는 DB 컬럼이 아니라 IVS 지표 조회 결과다.** 실시간 카운터를 DB에 두면 방송 중 매 초 UPDATE가 들어오고 그만큼 정확해지지도 않는다. 지표 조회가 실패하면 이 필드를 생략한다(목록 자체는 정상 응답).
- **정렬은 생성 최신순 단일 기준이다**(`createdAt desc, id desc`). 상태별로 기준이 갈리면
  `status`를 생략한 전체 조회에서 어느 쪽을 쓸지 정할 수 없다. `id` 보조 정렬은 `createdAt`이
  동률일 때 페이지 간 중복·누락을 막는다.

---

### LIVE 시청 정보 조회 (요구사항정의서 11.2.4)

```
GET /api/v1/lives/{liveId}/playback
```

Auth Required: **X**

Response Body

```json
{
  "status": "LIVE",
  "playbackUrl": "<https://xxx.live-video.net/.../master.m3u8?token=...&exp=>...",
  "linkedProject": { "projectId": "0198...", "title": "무선 미니 가습기", "achievementRate": 142 }
}
```

Validation / Business Rules

- **`playbackUrl`은 서명과 만료시간을 부여해 발급한다.** 원본 경로를 그대로 노출하지 않는다(S7 준용).
- **종료된 방송을 요청하면 다시보기(VOD) 정보로 자동 전환해 응답한다**(요구사항정의서 11.2.4). 클라이언트가 404를 받고 따로 VOD를 재요청하지 않아도 된다.
- `DRAFT`·`ERROR` 상태는 `404`로 응답한다 — 존재 여부 자체를 노출하지 않는다.
- 방송 화면에 상시 노출할 연동 프로젝트 배너 정보를 함께 내려준다(요구사항정의서 11.2.3).

---

### LIVE 좋아요 / 공유 링크 (요구사항정의서 11.2.4)

```
PUT    /api/v1/lives/{liveId}/like
DELETE /api/v1/lives/{liveId}/like
```

Auth Required: **O**

Response Body

```json
{ "liked": true, "likeCount": 128 }
```

```
GET /api/v1/lives/{liveId}/share-link
```

Response Body

```json
{ "shareUrl": "<https://service.example.com/live/0199c3a0->..." }
```

Validation / Business Rules

- **`PUT`/`DELETE` 모두 idempotent다.** 같은 요청을 반복해도 결과가 같다 — 하트 버튼은 더블탭·재시도가 일상이라 중복 요청을 409로 돌려주면 프론트가 "이미 좋아요함"을 성공으로 바꾸는 분기를 또 짜야 한다.
- 초안에는 `PUT`만 있어 **취소 경로가 없었다.** `DELETE`를 추가했다.
- `likeCount`는 `live_sessions.like_count` 캐시 값이다. 목록 조회마다 `COUNT(*)`를 돌리지 않기 위한 비정규화다.
- 공유 링크는 인증 없이 발급한다 — 공개 URL 조합일 뿐이다.

---

### 채팅 송수신 및 필터링 (요구사항정의서 11.3.4)

**이 서비스에 채팅 송수신 엔드포인트는 없다.** 클라이언트는 위 `POST /chat/token`으로 받은 토큰으로
IVS Chat에 직접 연결해 주고받는다.

부적절 메시지(욕설·XSS 등) 필터링은 **IVS Chat 리뷰 핸들러(Lambda)** 가 담당한다.

| 항목 | 내용 |
| --- | --- |
| 호출 시점 | 룸으로 오는 모든 `SendMessage` |
| 반환 | `ALLOW` / `DENY` / 내용 수정 |
| 차단 시 | 발신자에게 WebSocket `406`, `Attributes.Reason`에 사유 |
| 핸들러 장애 | 룸에 설정한 `FallbackResult`에 따라 통과/차단 |

Validation / Business Rules

- **`FallbackResult`는 `DENY`로 설정한다.** 필터가 죽었을 때 걸러지지 않은 메시지가 전 시청자에게 퍼지는 것보다, 그동안 채팅이 막히는 편이 낫다(`security.md` S2).
- **리뷰 핸들러에서 live-service DB를 호출하지 않는다.** 동기 경로라 우리가 느려지면 채팅이 느려지고, 우리가 죽으면 채팅이 막힌다. 적재는 Chat Logging으로 분리돼 있다.
- 진행 중이 아닌 방송에는 토큰이 발급되지 않으므로 전송 자체가 불가능하다.

---

### 답변된 질문 모아보기 (요구사항정의서 11.3.4) — 채팅창 Q&A 버튼

```
GET /api/v1/lives/{liveId}/chat/answered-questions
```

Auth Required: **X**

Response Body

```json
{
  "items": [
    { "questionId": "0199d1...", "summaryText": "일반 진공 청소 모드(물 없이 청소)도 가능한가요?",
      "questionCount": 12,
      "answerText": "기본 모드(AUTO/MAX/ECO)에서는 물 분사와 흡입이 동시에 작동하기 때문에 물 없이 진공 청소만 하는 것은 불가능합니다.",
      "answeredBy": "SELLER", "answeredAt": "2026-09-10T20:04:22+09:00" }
  ]
}
```

Validation / Business Rules

- 요구사항정의서 11.3.4의 *"채팅창에 AI 코파일럿 기능을 이용한 답변들을 모아볼 수 있는 Q&A 버튼"* 에 대응한다. 채팅이 폭주해 답변을 놓친 시청자가 다시 찾아보는 용도다.
- **`is_answered = true`인 대표질문만** 내려준다. 미답변 질문과 관심사 집계는 판매자 화면(`/chat/insights`)에만 노출된다 — 소비자에게 "아직 답 없는 질문 목록"을 보여줄 이유가 없다.
- **정렬은 질문 건수(`questionCount`) 내림차순이다.** 시안에서 12건 → 11 → 11 → 11 순으로 나열돼 있다. *(초안에 "답변 시각 최신순"으로 적었던 것은 시안 확인 전 추측이었다 — 정정)*
- **`questionCount`는 필수다.** 시안이 질문 아래 "질문 12건"을 표시한다 — 같은 질문을 여러 명이 했다는 신호이고, 이게 없으면 화면이 안 그려진다. `live_question_summaries.related_question_count` 값이다.
- `answeredBy`는 시안의 "판매자 · 1분 전" 표기용이다. 상대 시간은 `answeredAt`으로 클라이언트가 계산한다.
- 답변 본문은 `live_question_summaries.answer_text`에 저장된 값이다. 채팅 스트림은 지나가면 끝이라 전송한 답변을 따로 남긴다.
- 인증 불필요(방송 자체가 공개). 출력 시 인코딩한다(S2).

---

### 다시보기(VOD) 재생 / 시간대별 채팅 조회 (요구사항정의서 11.4.4)

```
GET /api/v1/lives/{liveId}/vod
```

Response Body

```json
{ "vodUrl": "<https://xxx.cloudfront.net/vod/xxx.m3u8?sig=...&exp=>...", "durationSec": 580,
  "markers": [ { "startSec": 320, "sceneLabel": "DEMO", "title": "실시간 시연" } ] }
```

```
GET /api/v1/lives/{liveId}/vod/chat?fromSec=180&toSec=210
```

Response Body

```json
{ "fromSec": 180, "toSec": 210, "messages": [ { "content": "...", "offsetSec": 185, "sentAt": "..." } ] }
```

Validation / Business Rules

- `vodUrl`도 서명·만료시간을 부여한다(S6·S7 준용).
- 다시보기 응답에 **공개된 타임라인 마커를 함께 내려준다** — 별도 호출 없이 재생바에 표시할 수 있게 한다.
- 채팅 조회는 초안의 단일 `position` 대신 **`fromSec`~`toSec` 범위**로 받는다. 재생 중 계속 조회하는 화면이라 시점 1개씩 왕복하면 요청 수가 방송 길이만큼 늘어난다.
- `offsetSec`은 `sent_at - actual_start_at`으로 계산한 재생 기준 오프셋이다.
- 조회 조건은 바인딩 변수로 처리한다(S1).

- **한 번에 조회할 수 있는 구간은 600초 이내다.** 초과하면 `400`.
  상한이 없으면 `fromSec=0&toSec=999999999` 한 번으로 방송 전체 채팅을 긁어간다.
  다시보기 UI가 재생 위치를 따라가며 짧은 구간을 반복 조회하는 형태라 페이지네이션은 두지 않는다.

---

### 라이브 쿠폰 발급(소비자) (요구사항정의서 16.6.4)

**이 서비스에 엔드포인트가 없다.**
클라이언트는 order의 `POST /api/v1/coupons/{couponCode}/claim`을 직접 호출한다.
재고 차감·1인 1매·유효기간 판정 전부 order 소관이고, "방송 진행 중" 여부만 order가 위 내부 API로 live에 물어본다.
상세는 위 "라이브 쿠폰 (요구사항정의서 16.6.4)" 절 참고.
