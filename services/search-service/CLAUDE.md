# search-service

> 루트 `CLAUDE.md`(레포 공통 규칙)와 `.claude/rules/`를 전제로, 여기는 search-service에만 해당하는 내용만 다룹니다.

## 이 서비스가 하는 일
홈피드 추천 프로젝트, 카테고리 탐색, 통합 키워드 검색(상품/LIVE/판매자), 최근·인기 검색어를 제공합니다. (`PRD.md` 10. 홈/카테고리/검색 기준)

**원본 데이터를 하나도 소유하지 않는 순수 read-model 서비스입니다.** 프로젝트 원본은 project-service, 판매자 정보는 member-service, LIVE는 (아직 없는) live-service, 펀딩 집계는 order-service 소유입니다. 이 서비스는 그 원본들의 읽기 전용 비정규화 사본(`project_documents` 등)을 자기 DB에 두고, 조회 요청마다 다른 서비스를 동기 호출하지 않게 합니다. **이벤트를 발행하지 않고 전부 구독만 합니다.**

## 먼저 읽을 문서
구현을 시작하기 전에 **`services/search-service/docs/SearchDomainFunctionalSpec.md`(SEARCH-001~015)를 먼저 읽으세요 — 그중에서도 SEARCH-011을 가장 먼저** 읽어야 합니다. API 계약은 `SearchDomainApiSpec.md`, DDL·설계 결정·확인 필요 사항은 `SearchERD.md`입니다. 이 CLAUDE.md는 그 문서들의 핵심만 요약한 것이지 대체하지 않습니다.

## ⚠️ 아직 남은 것 — SEARCH-013 + 게이트웨이 라우팅 확인

**SEARCH-011은 해결됐습니다.** project-service가 `project.approved.v1`/`project.updated.v1`을 발행하고(`ProjectIndexEventPublisher`/`ProjectIndexEventOutboxWorker`), 이 서비스가 `ProjectIndexEventKafkaListener` → `ProjectDocumentIndexSyncService`로 구독해 `project_documents`를 upsert합니다(`ProjectDocumentJpaRepository.upsertProjectInfo`). 게이트웨이 라우팅도 `platform:gateway-service` `application.yml`에 추가됐습니다(`/api/v1/home/**`, `/api/v1/categories/**`, `/api/v1/search/**` → `${downstream.search-service-base-url}`, 환경변수 `SEARCH_SERVICE_BASE_URL`).

남은 것:
- **SEARCH-013(펀딩 집계 동기화)은 여전히 막혀 있습니다.** project-service PROJECT-015가 참고하는 펀딩 집계 이벤트 자체가 미확정이라(`project_documents.current_amount`/`achievement_rate`/`participant_count`), 아직 착수할 수 없습니다.
- **project-service의 `SellerProfileClient`가 아직 Noop 구현체입니다.** member-service 동기 연동 전이라 `project_documents.seller_display_name`은 항상 `null`로 색인됩니다 — member-service 연동이 붙으면 자동으로 채워집니다(코드 변경 불필요, `SellerProfileClient` 구현체 교체만 필요).
- **`project.updated.v1`은 `updateBasicInfo`/`updateStory` 호출 시 프로젝트가 이미 공개(`isPublic()`) 상태일 때만 발행됩니다.** DRAFT/PENDING_REVIEW 단계의 수정은 애초에 색인에 없는 프로젝트라 발행하지 않습니다.

## 로컬 실행
- 앱 포트: `8089` (`application-local.yml`, gitignore 대상 — 커밋하지 않음)
- DB 포트: `5440`
- 최초 한 번: `.env.example`을 `.env`로 복사한 뒤 `docker compose up -d`

```bash
cp services/search-service/.env.example services/search-service/.env
cd services/search-service && docker compose up -d
```

## 구현 순서 권장 — PR 단위로 작게 쪼갠다

이 서비스는 애그리거트 대부분이 `persistence-convention.md` 2번("단순 애그리거트")·3번("조회 전용 프로젝션") 대상이라 `domain`/`Mapper`/`PersistenceAdapter`/포트 인터페이스를 생략할 수 있다. **PR 하나가 커지지 않도록 아래 단위로 쪼개서 진행한다** — 뒤 단계가 앞 단계의 파일을 재사용하도록 순서를 잡았다.

공용 응답 DTO 2개(`ContentResponse<T>`, `PageResponse<T>`)는 처음 필요해지는 시점에 한 번만 만들고 이후 전부 재사용한다. LIVE 탭/배너(SEARCH-002/006)는 항상 빈 배열을 반환하는 스텁이라 별도 컨트롤러·서비스 없이 이미 만든 컨트롤러에 메서드 하나만 추가한다.

1. **카테고리 조회(SEARCH-003)** — `CategoryJpaEntity`/`JpaRepository`, `CategoryQueryService`, `CategoryController`, `CategoryTreeResponse`, 시드 마이그레이션. 이 서비스 전체 레이어 패턴(엔티티→서비스→컨트롤러)을 세우는 첫 PR.
2. **홈피드 + 홈 LIVE 스텁(SEARCH-001, 002)** — `ProjectDocumentJpaEntity`/`JpaRepository`(이후 모든 프로젝트 관련 기능이 재사용), `ProjectCardProjection`(계산 필드 `projectDisplayCode`는 프로젝션 default 메서드로 처리), `HomeFeedQueryService`, `HomeController`, 공용 `ContentResponse<T>`.
3. **카테고리별 프로젝트 목록(SEARCH-004)** — `SearchErrorCode`(신규, `INVALID_CATEGORY`), 공용 `PageResponse<T>`. 나머지는 1·2번 파일에 메서드만 추가.
4. **통합검색 상품탭 + 최근검색어 자동저장 + 검색 LIVE 스텁(SEARCH-005, 006, 008)** — `SearchQueryLogJpaEntity`/`JpaRepository`, `RecentSearchKeywordId`(복합키)/`JpaEntity`/`JpaRepository`, `ProjectSearchService`, `SearchController`. 세 기능이 한 요청 경로 안에서 얽혀 있어 이 PR이 가장 크다 — 리뷰가 부담되면 "검색 실행(005/006)"과 "최근검색어 자동저장(008)"으로 한 번 더 쪼갠다.
5. **최근검색어 조회/삭제(SEARCH-009)** — `RecentKeywordService` 추가, 4번 엔티티 재사용, `SearchController`에 메서드만 추가.
6. **인기검색어 조회 + 집계배치(SEARCH-010, 015)** — `PopularSearchKeywordJpaEntity`/`JpaRepository`, `PopularKeywordAggregationScheduler`.
7. **판매자 탭 검색(SEARCH-007)** — `SellerSummaryJpaEntity`(`seller_summary` 뷰 매핑, `@Immutable`)/`JpaRepository`.
8. **이벤트 구독 — 펀딩 성립/미달(SEARCH-012)** — `KafkaConsumerConfig`(공용, 최초 1회 생성), `FundingStatusKafkaListener`. 기존 이벤트 재사용이라 새 이벤트 협의 없이 바로 착수 가능.
9. **이벤트 구독 — 찜 카운트(SEARCH-014)** — 멱등 가드 테이블 마이그레이션, `search_wish_stat_members` 네이티브 쿼리(project-service `project_wish_stat_members`와 동일 패턴), `WishEventKafkaListener`.
10. **이벤트 구독 — 프로젝트 색인 생성/갱신(SEARCH-011)** — project-service가 `project.approved.v1`/`project.updated.v1`을 발행하도록 먼저 만들어야 했다(아웃박스+Kafka, `RewardEventPublisher`와 동일 패턴). `ProjectIndexEventListener`/`ProjectDocumentIndexSyncService`/`ProjectIndexEventKafkaListener` + `ProjectDocumentJpaRepository.upsertProjectInfo`(project.approved.v1/updated.v1 공용 upsert, SEARCH-012/013/014가 관리하는 컬럼은 건드리지 않음). **완료됨.**

**SEARCH-013은 여전히 이 순서 밖이다** — project-service PROJECT-015의 펀딩 집계 이벤트 자체가 미확정이라 착수 불가. 이벤트가 확정되면 별도 PR로 진행한다.

## 도메인 테이블 (스키마 확정 — `V1__init_schema.sql`)
- `categories` — project-service `categories`의 읽기 전용 미러. 이벤트 동기화 대상이 아니다(마스터 데이터, Flyway 시드로만 관리). **전체 체계가 project-service와 아직 완전히 일치하지 않는다** — `SearchERD.md` 5-④ 참고.
- `project_documents` — 홈피드·카테고리·검색의 "상품" 색인. PK는 project-service `projects.id`를 그대로 쓴다(별도 서로게이트 키 없음). `status`는 `ONGOING`/`SUCCEEDED`/`FAILED`만 존재 — DRAFT/PENDING_REVIEW는 비공개라 애초에 색인 대상이 아니다.
- `live_documents` — 검색 LIVE 탭 색인용. live-service는 끝났고 `live.started.v1`/`live.ended.v1`도 발행 중이지만 컨슈머가 아직 없다. 홈 진행중 LIVE 배너는 이 테이블 없이 live-service `GET /api/v1/lives/banner`로 이미 해결됨. 검색 LIVE 탭이 실제 필요해질 때 컨슈머를 붙인다(YAGNI).
- `search_query_logs` — 실행된 모든 검색 원본 로그. `popular_search_keywords` 배치 집계의 소스.
- `recent_search_keywords` — 회원별 최근 검색어. `(member_id, keyword)` PK, upsert로 멱등 처리(member-service `wishes`와 동일 패턴).
- `popular_search_keywords` — 전역 인기 검색어 "현재 스냅샷"(이력 없음). 배치가 매 주기 TRUNCATE 후 재적재.
- `seller_summary`(VIEW) — `project_documents`를 `seller_id`로 집계. 별도 물리 테이블 없음(동기화 누락 위험 회피).

## 핵심 설계 결정 (구현 시 반드시 지킬 것)
- **원본을 절대 직접 쓰지 않는다.** 모든 갱신은 이벤트 구독(또는 SEARCH-015 내부 배치)을 통해서만 일어난다 — DB-per-service 원칙, `project_documents`는 project-service 원본의 최종적 일관성(eventual consistency) 사본이다.
- **검색은 PostgreSQL `pg_trgm`으로 시작한다(Elasticsearch 도입 안 함)[가정].** 이 레포 전체가 Postgres 단일 스택이라 새 인프라를 들이지 않는다. 정확도·트래픽 이슈가 실제로 나오면 재검토 대상.
- **`project_display_code`(예: `F0000123`)는 컬럼으로 저장하지 않는다.** project-service와 동일한 생성 규칙(`'F' || LPAD(id::text, 7, '0')`)을 API 응답 직렬화 시점에 `project_id`로부터 계산한다.
- **인기순 정렬은 `participant_count DESC, wish_count DESC`로 가정한다.** PM 확인 전 기본값 — `SearchERD.md` 5-⑥.
- **최근 검색어는 로그인 회원 전용으로 가정한다.** 비로그인 사용자의 최근 검색어는 클라이언트 로컬 저장에 맡긴다고 가정 — `SearchERD.md` 5-⑦, PM 확인 필요.
- **정책값(보관 개수 10개, 인기 검색어 집계 윈도우 24시간/갱신 주기 1시간/노출 10개)은 전부 PM 확인 전 기본값이다** — `SearchERD.md` 5-⑧.

## 이벤트 구독 (search-service는 발행하는 이벤트가 없음 — 순수 구독자)
| 기능 ID | 이벤트 | 발행 | 상태 |
| --- | --- | --- | --- |
| SEARCH-011 | `project.approved.v1`/`project.updated.v1`(신설됨) | project-service | ✅ 완료 |
| SEARCH-012 | `funding.succeeded.v1`/`funding.goal-failed.v1`(기존) | order-service | ✅ 컨슈머 그룹만 추가하면 됨 |
| SEARCH-013 | 펀딩 집계 이벤트(미확정, project-service PROJECT-015와 공유) | order-service | ❌ project-service 쪽도 미확정 |
| SEARCH-014 | `project.wished.v1`/`project.unwished.v1`(기존) | member-service | ✅ 컨슈머 그룹만 추가하면 됨 |
| SEARCH-015 | 스케줄러(내부 배치, Kafka 아님) | - | - |

`notification-service`의 `KafkaConsumerConfig` 패턴(소비 전용이라 자동구성 그대로 사용, `FixedBackOff(0,0)`으로 실패 메시지 한 번만 시도 후 다음으로 진행)을 그대로 따른다.

## 에러 코드
도메인 전용 코드는 `SearchErrorCode implements ErrorCode` 하나로 시작한다. 현재 확정된 신규 코드는 `INVALID_CATEGORY`(400, 존재하지 않는 카테고리 대/중분류 조합, SEARCH-004)뿐이다. 그 외(`INVALID_INPUT`, `UNAUTHORIZED`, 결과 없음)는 전부 기존 `CommonErrorCode`/빈 배열 응답을 그대로 쓴다.

## 이 서비스에서 절대 하지 말아야 할 것
- **SEARCH-011 이벤트가 없다고 API 계약·DB 스키마 작업까지 미루지 말 것** — 막힌 건 컨슈머 배선뿐이다. 컨트롤러/DTO/조회 쿼리는 지금 만들어도 되고, 색인이 비어 있으면 그냥 빈 배열을 반환하면 된다(에러 아님).
- **다른 서비스의 DB 테이블에 직접 접근하지 말 것** — project-service/member-service/order-service 원본은 오직 이벤트 구독으로만 반영한다.
- **`recent_search_keywords`/`search_query_logs`의 `member_id`를 `X-User-Id` 헤더에서 직접 파싱하지 말 것** — `@LoginUser CurrentUser`로 주입받는다(루트 `CLAUDE.md` 공통 규칙).
- **판매자 탭 응답(`/api/v1/search/sellers`)에 사업자 연락처 등 개인정보를 포함하지 말 것**(`security.md` S9).
- **검색 키워드를 SQL에 직접 결합하지 말 것** — `pg_trgm` 질의도 반드시 바인딩 변수로 전달한다(`security.md` S1).

## 남은 확인 필요 사항 (상세는 `docs/SearchERD.md` 5번, `docs/SearchDomainFunctionalSpec.md` 하단)
1. **[최우선] 펀딩 집계 이벤트 확정** — SEARCH-013, project-service PROJECT-015와 공동 이슈. 확정 전까지 카드의 달성률/참여자수는 계속 0이다.
2. LIVE 관련 전 기능(SEARCH-002, SEARCH-006) — live-service 착수 대기.
3. 카테고리 전체 체계 확정 및 project-service·search-service 간 동기화 방법.
4. 인기순 정렬 산출식, 최근/인기 검색어 정책값.
5. 비로그인 사용자의 최근 검색어 처리 방식(서버 저장 여부).
6. AI 개인화 추천(SEARCH-001 맞춤 추천 부분) 소유 서비스·구현 방식 확인.
7. project-service `SellerProfileClient`가 member-service 실제 연동 전 Noop이라 `seller_display_name`이 계속 `null`로 색인된다 — member-service 연동 완료 시 자동 해결.
