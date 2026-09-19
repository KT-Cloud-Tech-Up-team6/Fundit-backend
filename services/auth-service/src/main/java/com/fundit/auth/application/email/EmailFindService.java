package com.fundit.auth.application.email;

import com.fundit.auth.application.identity.IdentityVerificationStore;
import com.fundit.auth.domain.account.Account;
import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 찾기(AUTH-009). 두 단계다.
 *
 * <ol>
 *   <li>이름 + 전화번호 → <b>마스킹된</b> 이메일 ({@code 1234q***@gmail.com})</li>
 *   <li>본인인증 완료 → 이메일 전문</li>
 * </ol>
 *
 * <p><b>2단계는 요청 본문을 믿지 않는다.</b> 이름·전화번호를 다시 받지 않고
 * 본인인증 토큰에 실린 값({@link IdentityVerificationStore.VerifiedIdentity})으로 조회한다 —
 * 그 값은 본인인증 제공자가 준 것이다. 본문으로 받으면 1단계에서 알아낸 남의 번호로
 * 자기 인증 토큰을 붙여 전문을 꺼낼 수 있다.
 *
 * <p><b>1단계가 계정 존재 여부를 드러낸다.</b> 마스킹 이메일을 화면에 보여주는 게 요구사항이라
 * 피할 수 없다(`auth-service/CLAUDE.md`의 anti-enumeration 원칙과 충돌하는 지점).
 * 대신 응답 <b>형태</b>는 계정 유무와 무관하게 같고, {@link FindEmailAttemptLimiter}로
 * 번호를 바꿔가며 훑는 걸 막는다.
 */
@Service
@RequiredArgsConstructor
public class EmailFindService {

    private final AccountRepository accountRepository;
    private final IdentityVerificationStore identityVerificationStore;
    private final FindEmailAttemptLimiter attemptLimiter;

    /** 1단계. 가입된 계정이 없으면 {@code null}을 담아 같은 형태로 돌려준다. */
    @Transactional(readOnly = true)
    public String findMasked(String name, String phoneNumber) {
        if (attemptLimiter.exceeded(phoneNumber)) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        return accountRepository.findByNameAndPhone(name, phoneNumber)
                .map(account -> EmailMasker.mask(account.getEmail()))
                .orElse(null);
    }

    /**
     * 2단계. 본인인증 토큰을 1회 소비하고 그 안의 이름·전화번호로 계정을 찾는다.
     *
     * <p>계정이 없으면 404다 — 본인인증으로 <b>그 번호의 소유자임이 확인된</b> 사람에게
     * "가입 계정이 없다"고 답하는 건 열거가 아니다.
     */
    @Transactional(readOnly = true)
    public String reveal(String verificationToken) {
        var identity = identityVerificationStore.consume(verificationToken)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.TOKEN_INVALID));
        return accountRepository.findByNameAndPhone(identity.name(), identity.phoneNumber())
                .map(Account::getEmail)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}
