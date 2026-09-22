# Funding Story AI 로컬 스텁

| 항목 | 내용 |
|---|---|
| 목적 | AI 서버·모델 호출 없이 Funding Story 로컬 개발/QA |
| 위치 | `src/test/java/.../infrastructure/ai/stub/FundingStoryAiStubServer.java` |
| 실행 | 별도 Gradle 명령; Spring 자동 등록 없음; 운영 `bootJar`에 미포함 |
| 연결 | 실제 `HttpFundingStoryAiClient` → 스텁 → BE 내부 API |
| 데이터 | 메모리에만 유지; 재시작하면 새 세션 필요 |
| 결과 | `STUB / NOT AI GENERATED` 표시가 있는 테스트 PNG |

## 실행

| 전제 | 설정 |
|---|---|
| Java | BE와 동일한 Java 25 |
| BE | 기존 로컬 DB·인증·프로젝트·리워드 설정 필요 |
| 저장소 | 기존 S3 또는 호환 로컬 저장소 설정 필요; PNG PUT·BE 객체 검증을 우회하지 않음 |
| 공통 환경변수 | **두 터미널 모두** `INTERNAL_API_KEY`를 로컬 BE의 `internal-api.key`와 동일하게 지정 |
| 서비스 토큰 | **두 터미널 모두** `FUNDING_STORY_AI_SERVICE_TOKEN`에 동일한 로컬 전용 토큰 지정 |

저장소 루트에서 실행한다. 공유 개발/운영 자격증명이 아닌 **로컬 전용 값**을 사용한다.

```bash
# 터미널 1 — 스텁
./gradlew :services:project-service:fundingStoryAiStub

# 터미널 2 — 기존 로컬 설정으로 BE 실행
FUNDING_STORY_AI_BASE_URL=http://127.0.0.1:8000 \
SPRING_PROFILES_ACTIVE=local \
./gradlew :services:project-service:bootRun
```

| 스텁 환경변수 | 기본값 | 의미 |
|---|---|---|
| `FUNDING_STORY_STUB_PORT` | `8000` | `127.0.0.1`에만 바인딩 |
| `FUNDING_STORY_STUB_BE_URL` | `http://127.0.0.1:8083` | 콜백 수신 BE; localhost/127.0.0.1 HTTP 주소만 허용 |
| `FUNDING_STORY_STUB_RESULT` | `succeeded` | 아래 시나리오 선택 |
| `FUNDING_STORY_STUB_DELAY_MS` | `1500` | run 접수 후 콜백 작업 시작까지 지연 |
| `FUNDING_STORY_AI_SERVICE_TOKEN` | 없음·필수 | BE → 스텁 Bearer 토큰 |
| `INTERNAL_API_KEY` | 없음·필수 | 스텁 → BE 내부 API 키 |

## 시나리오

| 값 | PNG 업로드 | 완료 콜백 | 확인 대상 |
|---|---:|---|---|
| `succeeded` | 2개 | 성공 | 대표 이미지·본문 반영 |
| `partially_succeeded` | 1개 | 성공 이미지 + 실패 슬롯 | 부분 성공 결과·실패 슬롯 표시 |
| `failed` | 없음 | 실패·오류 정보 | 실패 표시·기존 스토리 유지 |
| `no_callback` | 없음 | 전송하지 않음 | BE 제한 시간 경과 후 조회 시 타임아웃 처리 |

```bash
FUNDING_STORY_STUB_RESULT=partially_succeeded \
./gradlew :services:project-service:fundingStoryAiStub
```

```text
세션 생성 → start → SSE(message/done) → 사용자 메시지 → SSE(message/done)
→ 세션 조회(요약·revision) → confirm → run 접수(queued)
→ BE upload-targets → 테스트 PNG PUT → BE completion → BE 결과 조회
```

| 지원 동작 | 내용 |
|---|---|
| 세션 | 생성·최신/ID 조회, 프로젝트 범위 분리 |
| 대화 | 고정 질문·요약; 첫 사용자 답변 후 확인 가능; SSE는 누적 text + done |
| 중복 요청 | start·동일 message_id·동일 run idempotency_key의 ID 재사용 |
| 충돌 | 오래된 revision·변경된 message_id 본문·동일 run 키의 다른 확인 revision은 409 |
| 콜백 재시도 | 일시 오류·연결 실패 및 BE run 등록 직후 404에 최대 4회; 본문 고정, 409는 중단 |
| 실패 확인 | 콘솔 `delivery failed`; 실패한 전송을 다른 결과로 바꿔 보내지 않음 |

## 검증·한계

```bash
./gradlew :services:project-service:test --tests '*FundingStoryAiStubServer*'
```

| 검증 | 방식 |
|---|---|
| 세션~완료 | 실제 BE HTTP 클라이언트·SSE·PNG PUT·콜백 ACK |
| 결과 반영 | 실제 `FundingStoryService.completeRun`; 테스트 DB/저장소 메타데이터 대역 |
| 안전성 | 잘못된 토큰·타 프로젝트·미확인 revision 거부; 외부 BE 주소 기동 거부 |
| 재시도 | 404/503 이후 동일 본문; 409 이후 추가 콜백 없음 |

실제 모델 품질·소요 시간·운영 S3/DB·TTL 만료·동시 작업 제어를 검증하는 도구는 아니다.
API 계약 전체 검증기의 대체가 아니며, 테스트 중 BE에 반영된 스토리는 자동 복원되지 않는다.
테스트 전용 프로젝트를 사용하고 종료는 `Ctrl+C`로 한다. 이미지 병렬화·운영 타임아웃 값은 변경하지 않는다.
