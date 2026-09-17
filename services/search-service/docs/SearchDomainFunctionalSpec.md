# Search 도메인 기능 명세서

> search-service의 상세 기능 명세입니다. 서비스 간 흐름·책임 경계는 루트 `docs/PRD.md`, 공통 규칙(에러코드/보안 등)은 `.claude/rules/`를 참고하세요.
> 출처: PM 요구사항정의서(10.1 홈 / 10.2 카테고리 탐색 / 10.3 검색) + `SearchERD.md`(ERD·설계 결정·확인 필요 사항)
> API 계약은 `SearchDomainApiSpec.md`를 참고하세요.

## 범위

이 문서는 search-service가 담당하는 **홈피드 상품 노출, 카테고리 탐색, 통합 키워드 검색(상품/LIVE/판매자), 최근·인기 검색어**를 다룹니다. 전부 조회 전용이며, 이 서비스가 원본 데이터를 생성·수정하는 기능은 없습니다 — 있는 것은 "다른 서비스가 만든 사실을 색인에 반영하는" 이벤트 구독 기능뿐입니다.

SEARCH-011(`project.approved.v1`/`project.updated.v1`)은 구현됐습니다. SEARCH-001·004·005·007은 `project_documents` 색인을 조회하며, 색인이 비어 있으면(콜드 스타트) 빈 결과를 반환합니다. 달성률·참여자수는 SEARCH-013이 붙기 전까지 0입니다.

AI 개인화 추천(홈피드의 "관심 카테고리·시청·펀딩 이력 기반 맞춤 추천")은 별도 AI 솔루션 영역으로 보고 이 문서 범위에서는 **비로그인/미동의 기준 인기순**만 다룹니다 — 현재 홈 피드는 `sort`/`personalized` 쿼리를 받지 않습니다.

---

## 1. SEARCH-001 — 홈 피드 조회 (추천 프로젝트 영역)

- **PRD 코드**: FL_B_HM_01_01
- **권한**: 공통(비로그인 포함)
- **담당 서비스**: search-service
- **대분류**: 소비자 / 홈·탐색
- **보안/권한 고려사항**: 없음(공개 데이터만 다룸)
- **소분류**: 홈 피드 추천 프로젝트 영역
- **예외 처리**: 색인된 프로젝트가 하나도 없음(콜드 스타트) → 빈 배열 반환, 별도 에러 아님 / 조회 실패 → 500 계열(클라이언트가 "콘텐츠를 불러오지 못했습니다" 안내, PRD 10.1.4)
- **요구사항**: 서비스 진입 시 최초로 보여줄 추천 프로젝트 목록을 제공한다
- **우선순위**: MVP
- **입력값**: (선택) `personalized`(AI 개인화 동의 여부 — 클라이언트가 로그인 회원 정보로 판단해 전달하거나, 서버가 `X-User-Id`로 회원 개인화 동의 여부를 조회. 후자는 member-service 동기 호출 필요 — 아래 검토의견 참고)
- **중분류**: 홈
- **처리 내용(기술)**: `project_documents`에서 `status='ONGOING'`인 행을 인기순(`participant_count DESC, wish_count DESC`)으로 조회. `personalized`/`sort` 쿼리는 받지 않으며 `size`는 기본 20·최대 100(null·1 미만은 20으로 처리, 400 아님). 개인화 동의 회원 대상 맞춤 추천은 이 기능 범위 밖(아래 참고)
- **출력값**: 프로젝트 카드 목록(projectId, title, thumbnailUrl, achievementRate, remainingDays 등 — API 명세서 참고)
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: PRD 10.1.3은 "AI 개인화 동의 회원은 관심 카테고리·시청·펀딩 이력 기반 맞춤 추천"이라고 명시하지만, 이건 검색/카탈로그 조회 로직이 아니라 추천 랭킹 알고리즘(AI 솔루션)의 영역이다. 이 문서는 **미동의/비로그인 회원 기준(인기·신규)만** SEARCH-001의 구현 대상으로 삼고, 개인화 추천은 별도 기능으로 분리해야 한다고 본다[범위 확인 필요 — PM/AI 솔루션 담당자].

---

## 2. SEARCH-002 — 홈 진행 중 LIVE 배너 조회

- **PRD 코드**: FL_B_HM_01_01
- **권한**: 공통
- **담당 서비스**: search-service
- **대분류**: 소비자 / 홈·탐색
- **보안/권한 고려사항**: 없음
- **소분류**: 홈 진행 중 LIVE 영역
- **예외 처리**: 진행 중 LIVE 없음 → 빈 배열(영역 자체 미노출은 프론트 처리)
- **요구사항**: 현재 방송 중인 LIVE의 썸네일·진입 배너를 홈 상단에 노출한다
- **우선순위**: MVP(PRD 기준) — **live-service 미착수로 스텁**
- **입력값**: 없음
- **중분류**: 홈
- **처리 내용(기술)**: `GET /api/v1/home/lives`는 항상 빈 배열을 반환한다. live-service가 생기면 `live_documents`에서 `status='LIVE'`인 행을 조회하도록 교체
- **출력값**: LIVE 카드 목록(liveId, title, projectId, thumbnailUrl, viewerCount) — 현재는 항상 빈 배열
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: **`SearchERD.md` 5-③과 동일한 이유로 데이터 소스가 없다.** live-service가 아직 코드조차 없어(`settings.gradle` 미포함) `live_documents`를 채울 이벤트가 없다. API 계약은 선반영했고, 실제 배포 전까지는 **항상 빈 배열을 반환하는 스텁**이다. live-service 착수 시점에 재검토 필요.

---

## 3. SEARCH-003 — 카테고리 목록 조회

- **PRD 코드**: FL_B_HM_01_02
- **권한**: 공통
- **담당 서비스**: search-service
- **대분류**: 소비자 / 홈·탐색
- **보안/권한 고려사항**: 없음(마스터 데이터 조회)
- **소분류**: 카테고리 탐색 진입
- **예외 처리**: 없음(마스터 데이터라 결과 없음 상황이 없음 — 시드가 비었다면 운영 이슈)
- **요구사항**: 카테고리 대/중분류 트리를 제공해 오른쪽 상단 카테고리 창(PRD 10.2.4)을 구성한다
- **우선순위**: MVP
- **입력값**: 없음
- **중분류**: 카테고리 탐색
- **처리 내용(기술)**: `categories`를 `display_order` 기준 정렬해 대분류별로 묶어 반환
- **출력값**: 대분류 → 중분류 목록 트리
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: `SearchERD.md` 5-④ 참고 — project-service의 현재 시드가 PRD 4.2.4 전체 체계와 표기가 달라(예: "홈리빙" vs "홈·리빙"), 카테고리 전체 확정 전까지는 이 목록이 최종본이 아니다.

---

## 4. SEARCH-004 — 카테고리별 프로젝트 목록 조회

- **PRD 코드**: FL_B_HM_01_02
- **권한**: 공통
- **담당 서비스**: search-service
- **대분류**: 소비자 / 홈·탐색
- **보안/권한 고려사항**: 없음
- **소분류**: 카테고리 필터링·정렬
- **예외 처리**: 결과 0건 → "해당 조건에 맞는 프로젝트가 없습니다" 안내용으로 `content: []` 반환(필터 초기화는 프론트 처리, PRD 10.2.4)
- **요구사항**: 선택한 대/중분류에 속하는 프로젝트 목록을 정렬·페이지네이션으로 제공한다
- **우선순위**: MVP
- **입력값**: `categoryMajor`(선택), `categoryMinor`(선택, `categoryMajor` 필요), `sort`(`POPULAR`\|`RECENT`\|`DEADLINE`), `page`, `size`(`page < 0` 또는 `size < 1` → `INVALID_INPUT`. `size` 최대 100)
- **중분류**: 카테고리 탐색
- **처리 내용(기술)**: `project_documents`에서 `status='ONGOING'` AND 카테고리 조건으로 필터링 후 `sort` 파라미터에 따라 정렬(SearchERD.md 인덱스 3종 활용)
- **출력값**: 페이지네이션된 프로젝트 카드 목록(`PageResponse<T>`, `api-convention.md` 표준 포맷)
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: 없음

---

## 5. SEARCH-005 — 통합 검색 실행 (상품 탭)

- **PRD 코드**: FL_B_HM_01_03
- **권한**: 공통
- **담당 서비스**: search-service
- **대분류**: 소비자 / 홈·탐색
- **보안/권한 고려사항**: [S1] 키워드를 SQL에 직접 결합하지 않고 바인딩 변수로 `pg_trgm` 유사도 질의에 전달
- **소분류**: 키워드 검색 — 상품(프로젝트) 탭
- **예외 처리**: 결과 0건 → "검색 결과가 없습니다" 안내 + 다른 키워드 검색 유도(PRD 10.3.4), 에러 아님
- **요구사항**: 입력 키워드로 프로젝트를 검색하고, 진행중/종료 하위 탭과 정렬(인기순/최신순/마감임박순)을 제공한다
- **우선순위**: MVP
- **입력값**: `keyword`(필수, 1자 이상), `subTab`(`ONGOING`\|`ENDED`), `sort`(`POPULAR`\|`RECENT`\|`DEADLINE`), `page`, `size`(`page < 0` 또는 `size < 1` → `INVALID_INPUT`. `size` 최대 100)
- **중분류**: 검색
- **처리 내용(기술)**: `project_documents.title`/`seller_display_name`에 대해 `pg_trgm` 유사도(`%` 연산자 또는 `ILIKE`) 매칭 → `subTab=ONGOING`이면 `status='ONGOING'`, `ENDED`면 `status IN ('SUCCEEDED','FAILED')` 필터 → `sort` 적용. 검색 응답을 먼저 반환하고, `search_query_logs` 적재는 Spring `ApplicationEvent` + `@Async` 핸들러가 수행한다(SEARCH-010의 소스). 로그 저장 실패는 검색 API에 전파되지 않는다
- **출력값**: 페이지네이션된 프로젝트 카드 목록
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: PRD 10.3.3의 "검색 정확도"는 형태소 분석 기반 한글 검색엔진을 전제로 한 기대일 수 있다 — `pg_trgm`은 완전한 형태소 분석이 아니라 문자 조합 유사도라 "프라이팬"으로 "후라이팬"류 오탈자는 어느 정도 잡지만, 조사가 붙거나 띄어쓰기가 크게 다른 경우 정확도가 떨어질 수 있다. `SearchERD.md` 설계 결정 1번 참고 — 정확도 이슈가 실제로 발생하면 Elasticsearch 도입을 재검토해야 한다.

---

## 6. SEARCH-006 — 통합 검색 실행 (LIVE 탭)

- **PRD 코드**: FL_B_HM_01_03
- **권한**: 공통
- **담당 서비스**: search-service
- **대분류**: 소비자 / 홈·탐색
- **보안/권한 고려사항**: [S1] SEARCH-005와 동일
- **소분류**: 키워드 검색 — LIVE 탭
- **예외 처리**: 결과 0건 → SEARCH-005와 동일 안내
- **요구사항**: 입력 키워드로 LIVE를 검색하고, 진행 중/진행예정 하위 탭을 제공한다
- **우선순위**: MVP(PRD 기준) — **live-service 미착수로 스텁**
- **입력값**: `page`, `size`(스텁 단계에서는 `keyword`/`subTab`을 받지 않음)
- **중분류**: 검색
- **처리 내용(기술)**: 항상 빈 페이지를 반환한다. 상품 탭과 달리 키워드 검증·검색 로그·최근검색어 저장을 하지 않는다
- **출력값**: 페이지네이션된 LIVE 카드 목록(현재는 항상 빈 결과)
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: SEARCH-002와 동일한 이유(`SearchERD.md` 5-③)로 현재는 항상 빈 배열을 반환하는 스텁이다. live-service 착수 후 실제 검색으로 교체할 때 SEARCH-005와 계약을 맞출 것.

---

## 7. SEARCH-007 — 통합 검색 실행 (판매자 탭)

- **PRD 코드**: FL_B_HM_01_03
- **권한**: 공통
- **담당 서비스**: search-service
- **대분류**: 소비자 / 홈·탐색
- **보안/권한 고려사항**: [S1] SEARCH-005와 동일 / [S9] 판매자 개인 연락처 등은 이 응답에 포함하지 않음(project-service PROJECT-021과 동일 원칙)
- **소분류**: 키워드 검색 — 판매자 탭
- **예외 처리**: 결과 0건 → SEARCH-005와 동일 안내
- **요구사항**: 입력 키워드로 판매자(메이커)를 검색한다
- **우선순위**: MVP
- **입력값**: `keyword`(필수, 1자 이상), `page`, `size`(`page < 0` 또는 `size < 1` → `INVALID_INPUT`. `size` 최대 100)
- **중분류**: 검색
- **처리 내용(기술)**: `seller_summary` 뷰에서 `seller_display_name`에 대해 `pg_trgm` 매칭 후 조회. SEARCH-005와 같이 `search_query_logs`에 비동기 기록한다(최근검색어 자동 저장은 하지 않음)
- **출력값**: 판매자 카드 목록(sellerId, sellerDisplayName, ongoingProjectCount, totalProjectCount)
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: 판매자 상세 정보(사업자유형·과거 프로젝트 이력 등)는 이 서비스가 갖고 있지 않다 — 카드 클릭 시 project-service의 `GET /api/v1/sellers/{sellerId}`(PROJECT-021)로 이동하는 것을 전제로 한다[가정].

---

## 8. SEARCH-008 — 최근 검색어 저장

- **PRD 코드**: FL_B_HM_01_03
- **권한**: 구매자(로그인)
- **담당 서비스**: search-service
- **대분류**: 소비자 / 홈·탐색
- **보안/권한 고려사항**: [S4] `@LoginUser CurrentUser`의 회원 ID만 사용, 요청 본문의 회원 식별자는 받지 않음
- **소분류**: 최근 검색어 자동 저장
- **예외 처리**: 없음(검색 실행 자체의 부수 효과라 별도 실패 응답 없음 — 저장 실패해도 검색 결과 응답은 그대로 반환)
- **요구사항**: 로그인 회원이 검색을 실행하면 해당 키워드를 최근 검색어로 저장한다
- **우선순위**: MVP
- **입력값**: 검색 실행 시(`SEARCH-005` 상품 탭만) 함께 전달되는 `keyword`, `X-User-Id`
- **중분류**: 검색
- **처리 내용(기술)**: `recent_search_keywords (member_id, keyword)`에 `ON CONFLICT (member_id, keyword) DO UPDATE SET searched_at = now()`로 upsert(멱등, member-service `wishes`와 동일 패턴). 이후 `member_id`당 보관 개수(정책값, 기본 제안 10개 — `SearchERD.md` 5-⑧)를 초과하는 오래된 행 삭제
- **출력값**: 없음(비로그인 검색 API 호출의 부수 효과, 별도 응답 없음)
- **트리거 방식**: API 호출(상품 탭 검색 실행에 내장 — LIVE 스텁·판매자 탭은 저장하지 않음)
- **검토의견(변경사항)**: 비로그인 사용자의 최근 검색어는 서버가 다루지 않고 클라이언트 로컬 저장에 맡긴다고 가정한다(`SearchERD.md` 5-⑦) — PM 확인 필요.

---

## 9. SEARCH-009 — 최근 검색어 목록 조회 / 개별·전체 삭제

- **PRD 코드**: FL_B_HM_01_03
- **권한**: 구매자(로그인)
- **담당 서비스**: search-service
- **대분류**: 소비자 / 홈·탐색
- **보안/권한 고려사항**: [S4] 본인 최근 검색어만 조회·삭제 가능
- **소분류**: 최근 검색어 조회·삭제
- **예외 처리**: 저장된 최근 검색어 없음 → 빈 배열 / 존재하지 않는 키워드 삭제 요청 → idempotent 204(찜 해제와 동일 원칙)
- **요구사항**: 검색창 진입 시 본인의 최근 검색어를 보여주고, 개별·전체 삭제할 수 있다
- **우선순위**: MVP
- **입력값**: (삭제 시) `keyword` 또는 전체삭제 플래그
- **중분류**: 검색
- **처리 내용(기술)**: 조회는 `recent_search_keywords WHERE member_id = ? ORDER BY searched_at DESC LIMIT N`. 삭제는 해당 회원의 행(개별 또는 전체) DELETE
- **출력값**: (조회) 최근 검색어 목록(keyword, searchedAt) / (삭제) 204 No Content
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: 없음

---

## 10. SEARCH-010 — 인기 검색어 조회

- **PRD 코드**: FL_B_HM_01_03
- **권한**: 공통
- **담당 서비스**: search-service
- **대분류**: 소비자 / 홈·탐색
- **보안/권한 고려사항**: 없음(비식별 집계 데이터)
- **소분류**: 인기 검색어 노출
- **예외 처리**: 집계 배치가 아직 한 번도 돌지 않음(콜드 스타트) → 빈 배열
- **요구사항**: 검색창 진입 시 전역 인기 검색어 상위 N개를 보여준다
- **우선순위**: MVP
- **입력값**: 없음
- **중분류**: 검색
- **처리 내용(기술)**: `popular_search_keywords`를 `rank` 순으로 전체 조회(SEARCH-015 배치가 채워둔 스냅샷을 그대로 읽기만 함)
- **출력값**: 인기 검색어 목록(rank, keyword)
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: 없음

---

## 시스템 — 색인 동기화(이벤트 구독·배치)

### 11. SEARCH-011 — 프로젝트 공개/수정 이벤트 구독 (색인 생성·갱신)

- **PRD 코드**: -
- **권한**: 시스템
- **담당 서비스**: search-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 이벤트 소스 검증(project-service가 발행한 이벤트인지 확인)
- **소분류**: 프로젝트 색인 생성·갱신
- **예외 처리**: 이미 색인된 `project_id`의 재수신 → upsert로 멱등 처리. `sourceVersion`(아웃박스 id)이 기존 행보다 낮으면 덮어쓰지 않음. 색인이 없는 `project.updated.v1`은 INSERT
- **요구사항**: project-service에서 프로젝트가 공개(승인)되거나 정보가 바뀔 때 `project_documents`를 동기화한다
- **우선순위**: MVP
- **입력값**: `project.approved.v1`/`project.updated.v1` — `projectId, publicId, sellerId, sellerDisplayName, title, thumbnailUrl, categoryMajor, categoryMinor, goalAmount, fundingStartAt, fundingDeadline, createdAt, sourceVersion`
- **중분류**: 색인 동기화
- **처리 내용(기술)**: 수신 payload로 `project_documents`에 upsert. UPDATE는 `source_version`이 더 큰 경우에만 반영. INSERT 시 `search_wish_stat_members`에 이미 쌓인 행 수로 `wish_count`를 재구성한다. `status`는 최초 생성 시 `ONGOING`
- **출력값**: 색인 처리 결과(컨슈머라 응답 없음)
- **트리거 방식**: 이벤트 구독
- **검토의견(변경사항)**: project-service가 `project.approved.v1`/`project.updated.v1`을 발행하고 search-service가 구독한다. `sourceVersion`은 토픽 간(찜 vs 공개) 순서 역전을 막기 위한 필드다.

### 12. SEARCH-012 — 프로젝트 성립/미달 이벤트 구독 (상태 전이)

- **PRD 코드**: -
- **권한**: 시스템
- **담당 서비스**: search-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 이벤트 소스 검증
- **소분류**: 프로젝트 종료 상태 반영
- **예외 처리**: 색인에 없는 `projectId`(SEARCH-011이 아직 도착하지 않은 경우) → `SearchIndexNotReadyException`. Kafka는 `search.kafka.retry`(기본 interval 1s, max-attempts 4)로 재시도하고 소진 건은 DLT. 역직렬화·변환 실패는 재시도 없이 즉시 DLT
- **요구사항**: 펀딩 마감 시 목표 달성/미달 결과를 색인의 `status`에 반영한다
- **우선순위**: MVP
- **입력값**: `funding.succeeded.v1`(`fundingId, projectId`), `funding.goal-failed.v1`(`fundingId, projectId`) — order-service가 이미 발행 중인 기존 이벤트를 그대로 재사용
- **중분류**: 색인 동기화
- **처리 내용(기술)**: `funding.succeeded.v1` 수신 시 해당 `project_id`의 `project_documents.status`를 `SUCCEEDED`로, `funding.goal-failed.v1` 수신 시 `FAILED`로 UPDATE
- **출력값**: 없음(컨슈머)
- **트리거 방식**: 이벤트 구독
- **검토의견(변경사항)**: 기존 `funding.succeeded.v1`/`funding.goal-failed.v1`을 구독한다. 색인이 없으면 재시도 후 DLT로 보낸다(가드 테이블을 선반영하지 않음).

### 13. SEARCH-013 — 펀딩 집계 동기화

- **PRD 코드**: -
- **권한**: 시스템
- **담당 서비스**: search-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 이벤트/응답 소스 검증
- **소분류**: 달성률·참여자 수 동기화
- **예외 처리**: 동기화 실패(대상 프로젝트 색인 없음 등) → 해당 건만 스킵, 다음 주기에 재시도
- **요구사항**: 프로젝트 카드에 노출할 현재 펀딩금액·달성률·참여자 수를 최신화한다
- **우선순위**: MVP — **선행 조건 미충족(아래 참고)**
- **입력값**: project-service PROJECT-015가 참고하는 것과 동일한 order-service 펀딩 집계 이벤트(미확정)
- **중분류**: 색인 동기화
- **처리 내용(기술)**: 수신 데이터로 `project_documents.current_amount/achievement_rate/participant_count/funding_stats_synced_at` UPDATE
- **출력값**: 없음
- **트리거 방식**: 이벤트 구독(또는 배치 폴링 — 아래 참고)
- **검토의견(변경사항)**: `SearchERD.md` 5-②와 동일 — project-service의 `PROJECT-015`(펀딩 현황 조회) 문서조차 "order-service가 발행하는 펀딩 집계 이벤트를 구독(갱신 주기 1일)"이라고만 적었을 뿐 그 이벤트가 `event-convention.md`에 없다. **project-service 담당자가 이 이벤트를 확정하는 시점에 search-service도 동일 이벤트에 컨슈머 그룹만 추가하면 되므로, 별도로 새 이벤트를 만들 필요는 없다** — 다만 그 전까지는 이 기능도 착수 불가.

### 14. SEARCH-014 — 찜 이벤트 구독 (인기도 집계)

- **PRD 코드**: -
- **권한**: 시스템
- **담당 서비스**: search-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 이벤트 소스 검증
- **소분류**: 찜 카운트 동기화
- **예외 처리**: 이미 반영한 (projectId, memberId) 조합 재수신 → 멱등 처리(중복 가감 방지). 색인이 없으면 가드 테이블을 건드리지 않고 `SearchIndexNotReadyException` → 재시도 후 DLT
- **요구사항**: 찜 등록·해제를 인기순 정렬에 반영할 수 있도록 프로젝트별 찜 수를 집계한다
- **우선순위**: MVP
- **입력값**: `project.wished.v1`/`project.unwished.v1`(member-service 발행) — `projectId, memberId`
- **중분류**: 색인 동기화
- **처리 내용(기술)**: `search_wish_stat_members`로 같은 `(project_id, member_id)` 이벤트의 중복 가감을 막고, 색인이 있을 때만 `project_documents.wish_count`를 증감. 색인 INSERT(SEARCH-011) 시 가드 테이블 행 수로 `wish_count`를 재구성
- **출력값**: 없음
- **트리거 방식**: 이벤트 구독
- **검토의견(변경사항)**: 기존 토픽을 `search-service` 컨슈머 그룹으로 구독한다. 색인 미도착 시 가드를 선반영하지 않는 이유는 SEARCH-011이 INSERT 때 가드 행 수로 카운트를 맞추기 때문이다.

### 15. SEARCH-015 — 인기 검색어 집계 배치

- **PRD 코드**: -
- **권한**: 시스템
- **담당 서비스**: search-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 배치 쿼리 바인딩 변수 사용
- **소분류**: 인기 검색어 집계
- **예외 처리**: 집계 대상 로그 0건(트래픽 없음) → 빈 스냅샷으로 갱신(이전 랭킹 유지가 아니라 초기화 — 정책 확인 필요)
- **요구사항**: 최근 검색 로그를 집계해 인기 검색어 상위 N개 스냅샷을 갱신한다
- **우선순위**: MVP
- **입력값**: (배치 트리거)
- **중분류**: 검색
- **처리 내용(기술)**: `search_query_logs WHERE searched_at > now() - interval '24 hours'`를 `keyword`로 GROUP BY·COUNT 후 상위 N개를 `popular_search_keywords`에 TRUNCATE 후 재적재(정책값: 집계 윈도우 24시간, 갱신 주기 1시간, 상위 10개 — `SearchERD.md` 5-⑧ 기본 제안값)
- **출력값**: 집계 결과(갱신된 랭킹 건수)
- **트리거 방식**: 스케줄러(배치)
- **검토의견(변경사항)**: 정책값 전부 PM 확인 전 기본값이다.

---

## 에러 코드 매핑

`error-handling.md` 기준으로, 공통 코드는 `CommonErrorCode`를 그대로 쓰고 새 코드만 `SearchErrorCode`(도메인 전용, `implements ErrorCode`)에 추가합니다.

| 상황 | 코드 | HTTP | 관련 항목 |
| --- | --- | --- | --- |
| 검색 키워드 누락/빈 문자열 | `CommonErrorCode.INVALID_INPUT`(기존) | 400 | SEARCH-005, SEARCH-007 |
| `page < 0` 또는 `size < 1` | `CommonErrorCode.INVALID_INPUT`(기존) | 400 | SEARCH-004, SEARCH-005, SEARCH-007 |
| 존재하지 않는 카테고리 대/중분류 조합 | `SearchErrorCode.INVALID_CATEGORY`(신규) | 400 | SEARCH-004 |
| 타인의 최근 검색어 삭제 시도 | 발생 불가 — `member_id`는 항상 `@LoginUser`에서 주입(요청 본문/경로로 타인 식별자를 받지 않음) | - | SEARCH-009 |
| 존재하지 않는 최근 검색어 삭제 요청 | 에러 아님 — idempotent 204 | - | SEARCH-009 |
| 검색·목록 결과 없음 | 에러 아님 — 빈 배열/빈 페이지 응답 | - | SEARCH-001, 004~007, 010 |
| project-service/order-service/member-service 이벤트 소스 검증 실패(신뢰할 수 없는 발행자) | `CommonErrorCode.INVALID_INPUT`(기존, 컨슈머 내부 처리) | - | SEARCH-011~014 |
| 비로그인 사용자의 최근 검색어 저장/조회/삭제 API 호출 | `CommonErrorCode.UNAUTHORIZED`(기존) | 401 | SEARCH-008, SEARCH-009 |

---

## 이벤트 발행/구독 요약

search-service는 **이벤트를 발행하지 않고 전부 구독만** 합니다(순수 read-model 서비스).

| 기능 ID | 이벤트/트리거 | 발행 | 상태 |
| --- | --- | --- | --- |
| SEARCH-011 | `project.approved.v1` / `project.updated.v1` | project-service | ✅ 발행·구독 완료(`sourceVersion` 포함) |
| SEARCH-012 | `funding.succeeded.v1` / `funding.goal-failed.v1`(기존) | order-service | ✅ 구독(색인 미도착 시 재시도 후 DLT) |
| SEARCH-013 | 펀딩 집계 이벤트(미확정, project-service PROJECT-015와 공유) | order-service | ❌ 미확정 — 달성률/참여자수는 0 |
| SEARCH-014 | `project.wished.v1` / `project.unwished.v1`(기존) | member-service | ✅ 구독(색인 미도착 시 가드 미반영, 재시도 후 DLT) |
| SEARCH-015 | 스케줄러(내부 배치, Kafka 아님) | - | ✅ 구현 |

> 색인 미도착·처리 실패는 `search.kafka.retry`로 재시도하고 소진 건은 DLT로 보낸다.

---

## ⚠️ 남은 확인 필요 사항 (요약 — 상세는 `SearchERD.md` 5번)

1. **펀딩 집계 이벤트 확정** — SEARCH-013, project-service PROJECT-015와 공동 이슈. 그 전까지 카드 달성률/참여자수는 0.
2. LIVE 관련 전 기능(SEARCH-002, SEARCH-006) — live-service 착수 대기, 현재는 빈 배열 스텁.
3. 카테고리 전체 체계 확정 및 project-service·search-service 간 동기화 방법.
4. 인기순 정렬 산출식, 최근/인기 검색어 정책값(보관 개수·집계 주기·노출 개수).
5. 비로그인 사용자의 최근 검색어 처리 방식(서버 저장 여부).
6. AI 개인화 추천(SEARCH-001의 맞춤 추천 부분) 소유 서비스·구현 방식 확인.
