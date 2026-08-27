---
paths:
  - "**/domain/**"
  - "**/infrastructure/persistence/**"
---

# 영속성 계층(Persistence) 컨벤션

애그리거트 복잡도에 따라 두 가지 방식을 구분해서 적용한다. "무조건 4파일 구조"가 아니라, 아래 판단 기준으로 먼저 분류한 뒤 그에 맞는 방식을 쓴다.

## 0. 애그리거트 복잡도 판단 기준

| 구분 | 기준 | 예시 |
|---|---|---|
| **복잡** | 불변식(예: 재고 0 미만 불가), 상태 전이 규칙, 동시성 제어, 여러 필드를 조합한 비즈니스 로직 중 하나라도 있음 | `Reward`(재고 차감), `Order`/`Funding`(상태 전이), `Payment`/`Refund`/`Settlement`(상태머신) |
| **단순** | 값을 그대로 저장·조회만 하고 검증/전이 로직이 거의 없음 | `TermsAgreement`(약관 동의 이력), `NotificationSetting`, `Wish`(찜), `ProjectNotice`(새소식) |

애매하면 "복잡"으로 분류한다(나중에 로직이 늘어나면 단순→복잡 전환이 그 반대보다 쉽다).

---

## 1. 복잡한 애그리거트 — 도메인/영속성 완전 분리

### 도메인 순수성 (필수)
- `domain` 패키지 클래스에는 JPA/Spring 애노테이션(`@Entity`, `@Table`, `@Column`, `@Component` 등)을 붙이지 않는다.
- 도메인 모델은 순수 Java로 작성한다. Lombok(`@Builder`, `@Getter` 등)은 프레임워크 종속이 아니므로 사용 가능.
- 리포지토리 **인터페이스(포트)** 는 `domain/{aggregate}/{Aggregate}Repository.java`에 정의한다. 구현체는 domain에 두지 않는다.

### 영속성 어댑터 구조
`infrastructure/persistence/{aggregate}/` 아래 4개 파일로 구성한다.

| 파일 | 역할 |
|---|---|
| `{Aggregate}JpaEntity` | `@Entity`, DB 컬럼 매핑 전용. Setter 없이 `@Builder` + `@AllArgsConstructor(access = PRIVATE)` + `@NoArgsConstructor(access = PROTECTED)` |
| `{Aggregate}JpaRepository` | `JpaRepository<{Aggregate}JpaEntity, ID>` 상속, Spring Data 메서드만 |
| `{Aggregate}Mapper` | 도메인 ↔ JpaEntity 상호 변환. **접근제한자 없이(package-private)** 작성해 어댑터 패키지 밖에서 JpaEntity를 직접 다루지 못하게 막는다 |
| `{Aggregate}PersistenceAdapter` | `domain.{aggregate}.{Aggregate}Repository` 구현. JpaRepository + Mapper를 조합해 포트 계약을 만족시킨다 |

### 참고 예시 (기존 payment-service 코드 기반)

```java
@Getter
@Entity
@Builder
@Table(name = "bank_account")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountJpaEntity {

    @Id
    private Long walletId;

    @Column(nullable = false, length = 10)
    private String bankCode;

    @Column(nullable = false)
    private Boolean isVerified;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.isVerified == null) this.isVerified = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

```java
public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, Long> {
}
```

```java
@Component
public class AccountMapper {

    Account toDomain(AccountJpaEntity entity) {
        return Account.builder()
                .walletId(entity.getWalletId())
                .bankCode(entity.getBankCode())
                .isVerified(entity.getIsVerified())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    AccountJpaEntity toEntity(Account domain) {
        return AccountJpaEntity.builder()
                .walletId(domain.getWalletId())
                .bankCode(domain.getBankCode())
                .isVerified(domain.getIsVerified())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
```

```java
@Component
@RequiredArgsConstructor
public class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository jpaRepository;
    private final AccountMapper mapper;

    @Override
    public boolean existsByAccount(Long walletId) {
        return jpaRepository.existsById(walletId);
    }

    @Override
    public Optional<Account> findById(Long walletId) {
        return jpaRepository.findById(walletId).map(mapper::toDomain);
    }

    @Override
    public Account save(Account account) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(account)));
    }
}
```

---

## 2. 단순 애그리거트 — Entity를 도메인 겸용으로 사용

- `domain` 패키지를 따로 만들지 않는다. Mapper·PersistenceAdapter·포트 인터페이스를 생략한다.
- `infrastructure/persistence/{aggregate}/`에 `{Aggregate}JpaEntity`와 `{Aggregate}JpaRepository`만 둔다.
- `application` 계층이 `JpaRepository`를 직접 주입받아 사용한다.
- 나중에 이 애그리거트에 실제 로직(불변식, 상태 전이 등)이 생기면 그때 1번 방식(완전 분리)으로 전환한다.

```java
// infrastructure/persistence/wish/WishJpaEntity.java
@Getter
@Entity
@Builder
@Table(name = "wish")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishJpaEntity {
    @Id
    private Long id;
    private Long memberId;
    private Long projectId;
    private LocalDateTime createdAt;
}
```

```java
// application에서 직접 사용 — Mapper/Adapter 없음
@Service
@RequiredArgsConstructor
public class WishService {
    private final WishJpaRepository wishJpaRepository;

    public void addWish(Long memberId, Long projectId) {
        wishJpaRepository.save(WishJpaEntity.builder()
                .memberId(memberId).projectId(projectId).build());
    }
}
```

---

## 3. 조회 전용(목록·검색) API — 프로젝션 예외

목록/검색처럼 **도메인 로직 없이 화면에 뿌릴 값만 필요한 조회**는 복잡한 애그리거트라도 도메인 재구성(Entity→Mapper→도메인)을 거치지 않고, Spring Data JPA의 **인터페이스 기반 프로젝션**으로 바로 응답 DTO 형태를 조회하는 것을 허용한다.

- 대상 예시: 카테고리 탐색, 통합검색, 홈피드, 서포터 목록처럼 쓰기 없이 읽기만 하는 API
- 프로젝션 코드는 `infrastructure/persistence/{aggregate}/query/`에 둔다 (쓰기 경로와 명확히 구분)
- `application`은 이 프로젝션 결과를 그대로 `presentation` 응답으로 반환해도 된다 (도메인 객체로 감쌀 필요 없음)

```java
// infrastructure/persistence/reward/query/RewardListProjection.java
public interface RewardListProjection {
    Long getId();
    String getName();
    Long getPrice();
    Integer getStockQuantity();
}
```

```java
public interface RewardJpaRepository extends JpaRepository<RewardJpaEntity, Long> {
    List<RewardListProjection> findByProjectId(Long projectId);
}
```

---

## 네이밍 규칙
- 존재 확인 메서드는 `existsBy...`로 시작한다 (Spring Data 컨벤션과 동일하게 3인칭 단수 `-s`. `existBy...`처럼 `-s`를 빠뜨리지 않는다)
- 생성 시각(`createdAt`)은 `@Column(updatable = false)`로 고정하고 `@PrePersist`에서만 설정, 수정 시각(`updatedAt`)은 `@PreUpdate`에서 갱신한다
- 매핑 방식은 MapStruct 등 자동 매퍼를 쓰지 않고 수동으로 변환 메서드를 작성한다 (복잡한 애그리거트의 Mapper에 한함)