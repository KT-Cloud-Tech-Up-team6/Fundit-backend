---
paths:
  - "**/src/test/**"
---

# 테스트 컨벤션

## 서비스당 테스트 파일 구성
대상 클래스마다 아래 중 필요한 것을 구성한다 (전부 필수는 아니고, 대상 로직 성격에 따라 선택).

| 테스트 유형 | 파일명 예시(`OrderService` 기준) | 설명 |
|---|---|---|
| 단위 테스트 | `OrderServiceTest` | Mockito로 의존성 목킹, 정상 흐름 검증 |
| 단위 예외 테스트 | `OrderServiceExceptionTest` | 예외 발생 케이스만 모아서 검증 |
| 통합 테스트 | `OrderServiceIntegrationTest` | `@SpringBootTest` + Testcontainers, 정상 흐름 |
| 통합 예외 테스트 | `OrderServiceIntegrationExceptionTest` | `@SpringBootTest` + Testcontainers, 예외 흐름 |
| 동시성 테스트 | `OrderServiceConcurrencyTest` | 동시성 이슈가 있는 로직(재고 차감 등)만 별도 분리 |

- 단위 테스트와 예외 테스트를 파일로 분리하는 이유: 한 파일에 정상/예외 케이스가 섞이면 커버리지·가독성 확인이 어려워지기 때문. 새 로직 작성 시 이 구분을 유지한다.
- 동시성 검증이 필요한 코드(비관적 락, 조건부 UPDATE, 재고 차감 등)를 발견하면 별도로 `ConcurrencyTest` 클래스를 만들어 동시 요청 시나리오를 검증한다.

## 커버리지
- JaCoCo 기준 라인 커버리지 80% (러프 기준, 예외적으로 미달 시 사유를 PR에 남길 것)

## 구조: Given-When-Then
각 단계를 주석으로 명시한다.

```java
@Test
void 재고가_충분하면_정상_차감된다() {
    // given
    Reward reward = RewardFixture.of(stock: 10);

    // when
    reward.decreaseStock(3);

    // then
    assertThat(reward.getStock()).isEqualTo(7);
}
```

## 테스트 메서드명
- 한국어로 작성, `{조건}_{기대하는_결과}()` 패턴 (조건이 먼저, 결과가 나중 — 한국어 어순 그대로)
- "-하면"이 이미 조건의 의미를 담고 있으므로 영어 `when`은 따로 붙이지 않는다
- 예: `재고가_충분하면_정상_차감된다()`, `재고가_부족하면_예외가_발생한다()`
- `@Nested`로 묶을 경우, 중첩 클래스명이 드러내는 주어(대상)는 메서드명에서 반복하지 않는다

## 케이스 분리 — @Nested
하나의 대상 메서드에서 여러 시나리오로 나뉘는 경우, 별도 테스트 메서드로 흩어놓지 않고 `@Nested` 내부 클래스로 묶는다.

```java
@Nested
class 재고_차감 {
    @Test
    void 충분하면_정상_차감된다() { ... }

    @Test
    void 부족하면_예외가_발생한다() { ... }
}
```

## Mock
- Mockito 사용 (`@Mock`, `@InjectMocks` 또는 `Mockito.mock(...)`)

## 통합 테스트
- `@SpringBootTest`로 스프링 컨텍스트를 띄워 검증
- 외부 인프라(DB, RabbitMQ 등 실제 사용하는 것만)는 Testcontainers로 대체 — Mock으로 대체하지 않는다