package com.fundit.auth.application.password;

import com.fundit.auth.application.mail.MailSender;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.auth.infrastructure.persistence.passwordresettoken.PasswordResetTokenJpaEntity;
import com.fundit.auth.infrastructure.persistence.passwordresettoken.PasswordResetTokenJpaRepository;
import com.fundit.auth.infrastructure.persistence.refreshtoken.RefreshTokenJpaRepository;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 비밀번호 재설정(AUTH-010). 링크 발송 → 링크로 변경, 두 단계다.
 *
 * <p><b>이름·전화번호·이메일 세 값이 모두 맞아야</b> 발송한다. 기존 스펙은 이메일만 받았는데
 * (`AuthDomainApiSpec.md`의 *"[가정] SMS 본인인증 없이 이메일 소유 확인만으로 진행"*),
 * 세 값을 받는 쪽이 그 가정을 부분적으로 메운다.
 *
 * <p><b>요청 응답은 계정 유무와 무관하게 항상 같다.</b> 여기서 404를 주면 이메일을 넣어보며
 * 가입 여부를 캐낼 수 있다(auth-service CLAUDE.md의 anti-enumeration). 이메일 찾기 1단계와
 * 달리 <b>돌려주는 내용이 없으므로</b> 완전히 같은 응답을 유지할 수 있다.
 */
@Service
public class PasswordResetService {

    private final AccountRepository accountRepository;
    private final PasswordResetTokenJpaRepository resetTokenRepository;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailSender mailSender;
    private final Duration tokenTtl;
    private final String resetUrlTemplate;

    public PasswordResetService(
            AccountRepository accountRepository,
            PasswordResetTokenJpaRepository resetTokenRepository,
            RefreshTokenJpaRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            MailSender mailSender,
            @Value("${password-reset.token-ttl}") Duration tokenTtl,
            @Value("${password-reset.url-template}") String resetUrlTemplate) {
        this.accountRepository = accountRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.tokenTtl = tokenTtl;
        this.resetUrlTemplate = resetUrlTemplate;
    }

    /** 세 값이 모두 맞으면 재설정 링크를 보낸다. 아니면 조용히 아무것도 하지 않는다. */
    @Transactional
    public void requestReset(String name, String phoneNumber, String email) {
        Account account = accountRepository.findByNameAndPhone(name, phoneNumber).orElse(null);
        if (account == null || !account.getEmail().equalsIgnoreCase(email)) {
            return;
        }

        // 재발급 시 이전 링크를 무효화한다 — 메일함에 쌓인 옛 링크가 계속 살아있으면 안 된다.
        resetTokenRepository.deleteAllByAccountId(account.getId());

        UUID tokenId = UuidCreator.getTimeOrderedEpoch();
        Instant now = Instant.now();
        resetTokenRepository.save(PasswordResetTokenJpaEntity.builder()
                .tokenId(tokenId)
                .accountId(account.getId())
                .expiresAt(now.plus(tokenTtl))
                .createdAt(now)
                .build());

        mailSender.send(account.getEmail(), "[펀딧] 비밀번호 재설정 안내",
                "아래 링크에서 비밀번호를 새로 설정해 주세요.\n" + resetUrlTemplate.formatted(tokenId));
    }

    /**
     * 링크로 비밀번호를 바꾼다.
     *
     * <p>토큰 확인과 폐기가 한 문장이다({@code DELETE ... RETURNING}) — 나누면 같은 링크를
     * 동시에 두 번 눌렀을 때 둘 다 통과한다. 두 번째 사용은 401이다.
     *
     * <p>바꾼 뒤 <b>그 계정의 refresh token을 전부 지운다.</b> 비밀번호를 바꾸는 이유가
     * 탈취 의심인데 기존 세션이 살아 있으면 바꾼 의미가 없다.
     */
    @Transactional
    public void confirmReset(String token, String newPassword) {
        UUID tokenId = parseTokenId(token);
        UUID accountId = resetTokenRepository.deleteAndReturnAccountId(tokenId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.TOKEN_INVALID));

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.TOKEN_INVALID));
        account.changePassword(passwordEncoder.encode(newPassword));
        accountRepository.save(account);

        refreshTokenRepository.deleteAllByAccountId(accountId);
    }

    /** 토큰은 UUID다. 형식이 깨진 값도 "없는 토큰"과 같은 401로 돌려보낸다. */
    private UUID parseTokenId(String token) {
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.TOKEN_INVALID);
        }
    }
}
