# live-service

> 루트 `CLAUDE.md`(레포 공통 규칙)와 `.claude/rules/`를 전제로, 여기는 live-service에만 해당하는 내용만 다룹니다.

## 이 서비스가 하는 일
라이브 커머스 방송 — LIVE 생성·설정·송출·종료, 실시간 채팅 저장, AI 큐시트/대표질문/하이라이트의 **저장과 노출**, LIVE 검증 게시물, 라이브 전용 쿠폰 발행. (`PRD.md` 6장·11장·16.6 기준)

**AI 분석 자체는 이 서비스가 하지 않습니다.** 큐시트 생성, 질문 클러스터링, 하이라이트 구간 판별은 AI 파트 소관이고, 이 서비스는 **요청을 걸고 결과를 받아 저장·노출**합니다. 그래서 AI가 죽어도 방송과 채팅은 살아 있어야 합니다.

영상 송출·채팅 전송도 직접 구현하지 않습니다 — **Amazon IVS(비디오)와 IVS Chat(채팅)** 이 담당하고, 이 서비스는 채널·룸 매핑과 메시지 원본 저장만 합니다.

## 먼저 읽을 문서
LIVE 관련 작업을 시작하기 전에 **`services/live-service/docs/LiveFunctionalSpec.md`를 먼저 읽으세요** — 기능별 요구사항, 경계(무엇을 여기서 안 하는지), 초안 대비 변경 사유가 있습니다. 이 CLAUDE.md는 그 문서의 핵심만 요약한 것이지 대체하지 않습니다.

API 계약은 `LiveDomainApiSpec.md`, DDL 정본은 `V1__init_schema.sql`입니다.

> 확인 상태: 27블록(MVP 11 / P1 9 / P2 7) 구현 완료 + AI Q&A/FAQ 실계약(v1) 연동. 엔드포인트 34개, 테스트 180개.
>
> **외부 연동 2개는 스텁이다.** 자격증명·계약이 확정되면 클래스 하나씩 추가하고 프로퍼티만 바꾼다.
> - `live.ivs.mode=stub` → `StubIvsClient`. 실제는 `AwsIvsClient`(AWS SDK ivs·ivschat, 의존성은 이미 있음) —
>   **채팅에 메시지를 쓰는 `SendMessage`류가 아직 없다.** AI 추천답변 `SEND`가 지금 저장·조회까지만
>   하는 이유가 이것이다(아래 "AI 추천답변은 자동 게시하지 않는다" 참고).
> - `live.ai.mode=stub` → `StubAiClient`, `=http` → `HttpAiClient`. **Q&A/FAQ는 AI팀 실계약(v1,
>   2026-09-17 E2E 검증 완료)으로 연동 완료됐다** — `prepare`/`updateContext`/`submitComments`/
>   `faq`/`faqComments`/`unanswered`/`unansweredDetail`/`registerSellerAnswer`/`summary`.
>   **큐시트도 실계약(2026-09-22 확정)으로 연동 완료됐다** — 아래 참고.
>   **하이라이트는 여전히 미확정**이라 `HttpAiClient.requestHighlights`는
>   `UnsupportedOperationException`을 던진다 — 그 계약이 나오면 채운다.
>
> **큐시트는 BE→AI 동기 호출로 확정됐다** — Q&A 코파일럿과 다른 AI 서버다(별도 base-url·토큰,
> `live.cuesheet-ai.*`). AI 응답이 최대 3분+ 걸릴 수 있어 판매자 요청 스레드가 아니라
> `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`로 분리된 스레드에서 부른다
> (`CueSheetService.onCueSheetGenerationRequested`). 콜백 수신 엔드포인트
> (`POST /internal/v1/lives/{liveId}/cue-sheet`)는 삭제됐다.
>
> **하이라이트는 여전히 콜백(push) 가정이다** — AI가 live의 내부 엔드포인트로 밀어주는 구조로
> 만들었다(`POST /internal/v1/lives/{liveId}/highlights`). 확인요청 회신이 "BE가 폴링"으로 오면
> 내부 엔드포인트를 빼고 AI job 식별자 컬럼과 폴링 스케줄러를 넣는다 —
> **도메인·서비스·컨트롤러는 그대로다.**
>
> **Q&A/FAQ는 이 문제가 없다** — 비동기 결과 자체가 없다. `ChatCommentBatchSender`가 3초 주기로
> 채팅을 배치 전송하면 그 HTTP 응답으로 바로 답변이 오고, 나머지 조회는 화면을 그릴 때마다
> BE가 동기 호출한다.
>
> **미결 1건**: 하이라이트 성과 통계의 `fundingConversionCount`. 조회·클릭은 소비자 공개 조회
> 엔드포인트가 세지만 펀딩 전환 기여는 order 집계가 필요하고 그 주체가 정해지지 않았다.
> 채울 수 없는 필드를 응답에 두면 프론트가 0을 실제 값으로 오해하므로 **응답에서 뺐다.**

## 도메인 테이블
- `live_channels` — IVS 채널(영구 자원). `seller_id` UNIQUE라 **판매자당 1개**다. 동시에 두 방송을 송출할 수 없다는 뜻이고, "동일 프로젝트 반복 LIVE"는 순차 진행이라 이 제약으로 충분하다.
- `live_sessions` — 개별 방송. `public_id`(UUID)가 외부 노출용 `liveId`이고 내부 PK는 BIGINT다. `status`는 `DRAFT`(생성 직후) → `SCHEDULED`(예정일시 입력) → `LIVE` → `ENDED`, 송출 실패는 `ERROR`. `vod_url`·`like_count`를 함께 들고 있다.
- `live_cue_sheets` — AI 큐시트. **세션당 1행**이고 구간 배열은 `segments` JSONB다. `status=GENERATING`이 중복 생성 요청 차단의 근거다.
- `live_likes` — 좋아요. PK `(session_id, member_id)` 자체가 중복 방지다.
- `live_highlights` — **마커와 클립을 한 테이블에 `kind`로 구분**한다. `is_public` 기본값이 FALSE인 게 "판매자 확정 전 비공개" 정책이다.
- `chat_messages` — 원본 채팅. `sent_to_ai_at`으로 AI 배치 전송 여부를 표시한다(`ChatCommentBatchSender`가 3초 주기로 미전송분만 골라 보낸다). `question_summary_id`는 더 이상 채워지지 않는다 — 대표질문 원본은 이제 AI의 `GET /faq/{qid}/comments`에서 그때그때 받아온다(로컬 사본을 두지 않는다). IVS Chat Room ARN은 `live_sessions.ivs_chat_room_arn`에 있다 — 세션당 1개라 테이블로 빼지 않았다.
- `live_question_summaries` — AI FAQ 클러스터. `ai_question_id`가 AI의 `qid`(FAQ 계열)다. AI 응답을 그대로 upsert할 뿐 여기서 다시 집계하지 않는다.
- `live_event_outbox` — `live.ended.v1` / `live.questions-summarized.v1` 발행용. payload가 배열이라 JSONB다.

## 핵심 설계 결정 (구현 시 반드시 지킬 것)
- **`liveId`는 `public_id`(UUID)다**: API 경로·공유 링크가 전부 이 값이다. 내부 PK(BIGINT)를 URL에 노출하면 전체 방송 수가 추측된다. 서비스 간 통신에서만 BIGINT를 쓴다.
- **`projectId`는 바깥에서 UUID, 안에서 BIGINT**: 외부 API는 project-service의 `public_id`를 받고, 저장은 내부 `id`로 변환한다. 초안이 이 둘을 섞어 써서 타입이 어긋나 있었다.
- **자체 WebSocket 서버를 만들지 않는다**: 채팅 전송 계층은 IVS Chat이고, 클라이언트는 `POST /api/v1/lives/{liveId}/chat/token`으로 받은 토큰으로 IVS에 직접 붙는다. 우리가 중계하면 동시 접속만큼의 커넥션을 떠안으면서 얻는 게 없다. `CreateChatToken`은 백엔드만 호출할 수 있어 **발급 지점이 곧 인가 지점**이다.
- **리뷰 핸들러(필터링)와 적재를 분리한다**: 리뷰 핸들러는 `SendMessage`마다 호출되는 동기 경로다. 거기서 live-service DB를 호출하면 우리가 느려질 때 시청자 채팅이 느려지고, 우리가 죽으면 `FallbackResult`에 따라 채팅이 막히거나 필터링 없이 통과한다. 적재는 Chat Logging(Firehose, 지연 10초) → 내부 수집 엔드포인트로 뺀다. S3(5분)는 AI 3분 주기 집계에 못 맞춘다.
- **AI 실패가 방송을 막지 않는다**: 큐시트·대표질문·하이라이트 API가 실패해도 송출과 채팅은 정상이어야 한다(`PRD` 6.4.4.2). AI 호출을 방송 시작·채팅 전송 경로의 동기 의존으로 만들지 말 것.
- **AI는 우리가 조립한 컨텍스트만 받는다**: 흐름은 `FE → BE → AI → BE → FE`이고 FE는 AI 서버를 직접 부르지 않는다(협의 확정). 호출 구현은 `auth-service`의 `PortOneRestClient` 패턴을 그대로 쓴다 — `RestClient` + connect/read 타임아웃 명시, 실패는 `DependencyFailureException`, 응답은 구조 검증 후 사용(S7). 기본 타임아웃을 그대로 두면 AI가 느려질 때 방송 화면이 같이 멈춘다.
- **자동 생성물은 비공개로 시작한다**: 하이라이트는 `is_public=false`가 기본이고 판매자가 확정해야 소비자에게 보인다(`PRD` 6.6.3). 기본값을 TRUE로 바꾸면 검수 전 내용이 그대로 새어나간다.
- **AI 추천답변은 자동 게시하지 않는다**: `GENERATE`로 초안만 만들고 판매자가 `SEND`해야 `registerSellerAnswer`에 등록되고 `live_question_summaries`가 갱신된다. **`SEND`가 지금 실제 채팅에 게시하지는 않는다** — `IvsClient`에 `SendMessage`류가 없어서다(위 "외부 연동 2개는 스텁이다" 참고). 채팅 게시는 `AwsIvsClient`가 생기는 별도 작업으로 미뤄졌다. 환불·결제·배송 등 **정책 항목은 요약·재구성하지 않고 등록된 원문 그대로** 내보낸다(`PRD` 6.4.3).
- **근거 없는 답변을 만들지 않는다**: 상품 질문의 근거 범위는 리워드 기본 정보와 상세페이지뿐이다. AI가 근거를 못 찾으면 답변을 생성하지 않고 미답변으로 분류해 판매자에게 넘긴다 — 판매자 화면은 `referenceChunks`가 빈 것으로 이 상태를 안다.
- **AI 컨텍스트는 값이 실제로 바뀌는 지점에서만 갱신한다**: `prepare`는 방송 시작 시 1회(상품이 바뀌면 재호출), `updateContext`는 방송 설정 저장 시. 둘 다 트랜잭션 커밋 후에 호출해 AI 실패·지연이 방송 시작/설정 저장 자체를 막지 않는다. 폴링은 두지 않는다(YAGNI).
- **쿠폰 재고는 원자적 UPDATE로 차감한다**: `UPDATE ... SET remaining_quantity = remaining_quantity - 1 WHERE id = ? AND remaining_quantity > 0`. 조회 후 차감하면 방송 중 동시 요청에서 초과 발급이 난다.
- **쿠폰은 order-service 소관이다. live는 상태만 답한다**: order `coupons`가 생애주기 전체를 갖고 있고 `issue_channel='LIVE'`·`live_session_id`·`drop_type`·낙관적 락까지 이미 있다. live는 `GET /internal/v1/lives/{liveId}/status`로 "이 방송이 진행 중인가"만 답한다. **호출 방향이 order → live다** — 쿠폰의 주인이 order이므로 판정 정보를 그쪽이 가져간다.
- **질문요약은 Kafka로 밀어준다**: project-service가 `live_verifications`에 판매자 답변을 붙이려면 질문요약이 먼저 있어야 한다. 폴링이 아니라 `live.questions-summarized.v1` 발행이다(담당자 협의 확정).
- **`live.ended.v1` 발행은 아웃박스로 한다**: 이 이벤트 하나가 AI 질문요약(요구사항정의서 6.5.4.1)과 하이라이트 생성(요구사항정의서 6.6.4) 두 개의 트리거다. 유실되면 방송 종료 후 자산이 하나도 안 만들어진다.
- **시청자 수는 DB에 두지 않는다**: IVS 지표를 조회해서 채운다. 실시간 카운터를 DB에 두면 방송 중 매 초 UPDATE가 들어오고 그만큼 정확해지지도 않는다.
- **[천장] 판매자당 채널 1개**: 동시 송출 요구가 실제로 생기면 `uq_live_channels_seller`를 풀고 채널 풀 관리를 도입한다. 지금은 순차 방송만 가능하다.

## 에러 코드
도메인 전용 코드는 `LiveErrorCode implements ErrorCode`로 만든다(서비스당 flat enum 1개 — `error-handling.md` 컨벤션). 다만 **상당수는 `CommonErrorCode`로 충분하다** — `NOT_FOUND`(없는 LIVE), `FORBIDDEN`(타인 소유), `INVALID_INPUT`, `CONFLICT`(중복 생성·상태 위반)는 이미 있으니 재정의하지 않는다.

이 서비스 고유로 필요한 것은 **송출 오류**뿐이다 — AI 연동은 새 `LiveErrorCode`를 만들지 않고
`CommonErrorCode`로 흡수했다(전체 표는 `LiveDomainApiSpec.md` "AI 연동" 절).

**Q&A/FAQ(실계약 v1, 확정)**: AI의 `409 NOT_PREPARED`(`submitComments`) → `CommonErrorCode.CONFLICT`,
AI의 `404`(`unansweredDetail`/`registerSellerAnswer`) → `CommonErrorCode.NOT_FOUND`, 그 외
전부(`401` 등) → `DependencyFailureException`(503). `EVIDENCE_UNAVAILABLE`(근거 없음)은
**에러가 아니라 정상 응답의 한 상태**다 — `200`으로 내리고, 판매자 화면은 `referenceChunks`가
비어 있는 것으로 그 상태를 판단한다(AI의 `GET /unanswered/{qid}` 응답에 grounded 플래그가 없다).
근거 없음을 에러로 올리면 `PRD` 6.4.4.5가 요구하는 Empty State를 그릴 수 없다.

**큐시트·하이라이트 에러코드 매핑(미확정 초안)**: 전송 방식(큐시트는 동기 호출)은 확정됐지만,
AI가 실패를 어떤 코드로 주는지는 아직 논의된 적이 없다 — 아래는 AI팀과 확인되지 않은 가정이다.

| `LiveErrorCode` | status | AI 원본(가정) | 왜 `CommonErrorCode`로 안 되나 |
| --- | --- | --- | --- |
| `CUE_SHEET_NOT_READY` | 409 | `NOT_READY_TO_GENERATE` | 409가 `CONFLICT` 하나뿐이라 위 Q&A `NOT_PREPARED`와 합쳐진다 |
| `AI_GENERATION_FAILED` | 503 | `502 GENERATION_FAILED` | 502가 없다. 재시도 가능한 생성 실패와 AI 서버 다운(`DEPENDENCY_FAILURE`)은 다른 UI다 |

`422 VALIDATION_ERROR`는 `CommonErrorCode.BUSINESS_RULE_VIOLATION`으로 매핑한다(가정).

IVS·AI 연동 실패는 `DependencyFailureException`으로 감싼다(infrastructure 계층).

## 이 서비스에서 절대 하지 말아야 할 것
- **AI 서버에 live DB 접근을 주지 말 것** — 커넥션 정보·리드 레플리카 포함이다. 컨텍스트는 요청 바디로만 전달한다. AI가 우리 스키마에 의존하면 컬럼 하나를 바꿀 때마다 AI 파트와 배포 일정을 맞춰야 하고, 레포 규칙("다른 서비스의 DB 테이블에 직접 접근하지 말 것")을 AI에만 면제해주는 셈이 된다. AI 파트가 기존에 DB 직결 구조로 인지하고 있었으므로 특히 명시한다(협의 확정)
- **쿠폰 테이블·클레임 엔드포인트를 만들지 말 것** — order `coupons`가 전부 갖고 있다. 특히 **소비자 클레임을 live가 감싸지 말 것** — 재고 차감 경로가 두 개가 되는 순간 order의 낙관적 락이 지켜주던 정합성이 깨진다
- **LIVE 검증 답변을 여기 저장하지 말 것** — project-service `live_verifications`가 판매자 답변을 갖는다. 여기 또 두면 답변이 두 군데 생겨 정본이 사라진다. live는 `live_question_summaries`까지만 맡는다
- **`live_notify_requests`를 여기 만들지 말 것** — notification-service가 이미 소유한다. 알림 발송 주체가 거기라 그쪽이 맞고, 반대로 두면 발송할 때마다 live에 "누가 신청했나"를 물어야 하며 live가 죽으면 알림도 못 나간다. 그쪽 테이블의 `live_id`(UUID)는 이 서비스의 `live_sessions.public_id`와 같은 값이다
- **판매자 팔로우 테이블을 만들지 말 것** — member-service `follows`가 이미 있다. "관심 판매자"는 회원의 목록이지 방송 도메인 자산이 아니다
- **`ivs_stream_key_ref`에 실제 스트림 키를 넣지 말 것** — 탈취되면 타인이 이 채널로 무단 송출한다. 비밀관리 시스템의 참조 식별자만 저장하고 값은 DB 밖에 둔다(`security.md` S9). 방송 시작 응답에도 담지 않는다
- **`playbackUrl`·`vodUrl`을 서명·만료 없이 내보내지 말 것** — 원본 경로가 그대로 나가면 URL만으로 무제한 재생이 가능하다(S6·S7 준용)
- **채팅을 검증 없이 브로드캐스트하지 말 것** — 부적절 메시지 필터링은 서버(또는 IVS Chat 검토 핸들러) 책임이다. 클라이언트 필터링만 믿지 않는다(S2)
- **`DRAFT` 상태를 소비자 목록·시청 API에 노출하지 말 것** — 설정이 끝나지 않은 방송이다
- **AI 응답을 검증 없이 저장·노출하지 말 것** — 구조·길이를 확인하고, 소비자 화면에 나가는 값은 인코딩한다(S2·S7)
- **다른 서비스 테이블에 직접 접근하지 말 것** — 프로젝트·리워드 정보는 API 또는 이벤트로만 가져온다(루트 `CLAUDE.md`)
- **채팅 토큰을 클라이언트가 만들게 하지 말 것** — `CreateChatToken`은 서버만 호출한다. 클라이언트에 AWS 자격증명이 나가면 임의 권한 토큰을 스스로 발급한다
- **capabilities·표시명을 요청 본문으로 받지 말 것** — 호출자를 보고 서버가 정한다. 클라이언트가 보낸 표시명을 토큰 `attributes`에 그대로 실으면 타인 사칭이 된다
- **채팅 적재 엔드포인트(`/internal/v1/lives/chat/messages`)를 게이트웨이 라우팅에 노출하지 말 것** — 노출되면 외부에서 임의 채팅을 주입할 수 있다. Firehose는 게이트웨이를 경유하지 않으므로 `InternalGatewaySecretFilter`를 그대로 적용하면 전량 401이 난다 — 별도 인증 경로가 필요하다
- **`X-User-Id` 헤더를 직접 파싱하지 말 것** — `@LoginUser CurrentUser`로 주입받는다(루트 `CLAUDE.md` 공통 규칙)
