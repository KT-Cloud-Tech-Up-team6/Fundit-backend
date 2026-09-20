package com.fundit.auth.infrastructure.persistence.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {

    /**
     * 이메일은 암호문으로 저장돼 평문 비교가 안 된다 — 블라인드 인덱스로 찾는다.
     * 평문 → 해시 변환은 {@code AccountPersistenceAdapter}가 한다.
     */
    Optional<AccountJpaEntity> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    /**
     * 이메일 찾기(AUTH-009)·비밀번호 재설정(AUTH-010) 조회용. 이름까지 같이 본다.
     *
     * <p><b>{@code List}인 이유</b>: 회원가입은 이메일 중복만 막고 이름+전화번호 중복은 막지
     * 않는다({@code SignupService}). 같은 사람이 이메일만 바꿔 두 번 가입하면 해시가 같은 계정이
     * 2개 생기는데, 여기가 {@code Optional}을 반환하면 결과가 2건일 때 Spring Data가
     * {@code IncorrectResultSizeDataAccessException}을 던져 두 API가 500이 된다.
     * 가장 최근 계정을 고르는 선택은 {@code AccountPersistenceAdapter}가 한다 — 중복 가입 자체를
     * 막을지는 별도 정책 결정이라 이 조회는 "있는 것 중 하나를 안전하게 고른다"까지만 한다.
     *
     * <p>2차 정렬로 {@code id DESC}를 더한다. 같은 밀리초에 두 계정이 생기면 {@code createdAt}만으론
     * 동률이 나올 수 있는데, id는 전부 {@code UuidCreator.getTimeOrderedEpoch()}로 만들어져
     * ({@code SignupService}, {@code SocialSignupService}) 값 자체가 생성 시각 순서를 보장한다 —
     * 임의 타이브레이커가 아니라 실제 가입 순서다.
     */
    List<AccountJpaEntity> findByPhoneHashAndNameHashOrderByCreatedAtDescIdDesc(String phoneHash, String nameHash);

    Optional<AccountJpaEntity> findBySocialProviderAndSocialId(String socialProvider, String socialId);

    /**
     * TokenRefreshService의 회전/재사용탐지 경합을 계정 단위로 직렬화하기 위한 전용 락 조회.
     * 다른 곳(findById)에는 영향 없도록 별도 메서드로 분리한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountJpaEntity a where a.id = :id")
    Optional<AccountJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
