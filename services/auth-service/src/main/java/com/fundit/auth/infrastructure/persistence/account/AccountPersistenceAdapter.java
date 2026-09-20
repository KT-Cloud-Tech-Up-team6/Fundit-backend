package com.fundit.auth.infrastructure.persistence.account;

import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.domain.account.SocialProvider;
import com.fundit.auth.infrastructure.security.BlindIndex;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository jpaRepository;
    private final AccountMapper mapper;
    private final BlindIndex blindIndex;

    /**
     * 평문을 해시로 바꾸는 유일한 지점이다. 도메인은 평문만 알고, 애플리케이션 계층은
     * 블라인드 인덱스가 있다는 것도 모른다.
     *
     * <p>이름·전화번호는 <b>가입 때만 실려 온다</b>(읽어온 Account에는 null이다).
     * 그래서 갱신 저장에서 기존 해시를 null로 덮지 않도록 기존 행 값을 유지한다.
     */
    @Override
    public Account save(Account account) {
        String phoneHash = blindIndex.of(BlindIndex.LABEL_PHONE, account.getVerifiedPhoneNumber());
        String nameHash = blindIndex.of(BlindIndex.LABEL_NAME, account.getVerifiedName());
        if (phoneHash == null && nameHash == null) {
            var existing = jpaRepository.findById(account.getId()).orElse(null);
            if (existing != null) {
                phoneHash = existing.getPhoneHash();
                nameHash = existing.getNameHash();
            }
        }
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(account,
                blindIndex.of(BlindIndex.LABEL_EMAIL, account.getEmail()), phoneHash, nameHash)));
    }

    @Override
    public Optional<Account> findBySocial(SocialProvider provider, String socialId) {
        return jpaRepository.findBySocialProviderAndSocialId(provider.name(), socialId).map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    /**
     * 평문을 해시로 바꿔 조회한다 — 포트 시그니처가 평문 그대로라 애플리케이션 계층은
     * 암호화를 모른다. 이메일 조회 경로 세 곳이 전부 이 메서드를 지난다.
     */
    @Override
    public Optional<Account> findByEmail(String email) {
        return jpaRepository.findByEmailHash(blindIndex.of(BlindIndex.LABEL_EMAIL, email))
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmailHash(blindIndex.of(BlindIndex.LABEL_EMAIL, email));
    }

    /** 해시가 같은 계정이 여러 개면 가장 최근 가입한 계정을 쓴다({@code AccountJpaRepository} 참고). */
    @Override
    public Optional<Account> findByNameAndPhone(String name, String phoneNumber) {
        return jpaRepository.findByPhoneHashAndNameHashOrderByCreatedAtDescIdDesc(
                        blindIndex.of(BlindIndex.LABEL_PHONE, phoneNumber),
                        blindIndex.of(BlindIndex.LABEL_NAME, name))
                .stream().findFirst()
                .map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void lockForUpdate(UUID id) {
        jpaRepository.findByIdForUpdate(id);
    }
}
