package com.fundit.auth.infrastructure.seed;

import com.fundit.auth.application.signup.SignupService;
import com.fundit.auth.domain.account.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * dev(QA) 환경 테스트 계정 2개를 기동 시 만든다 — 판매자용 A, 서포터용 B.
 *
 * <p><b>dev 전용이다.</b> {@code test-accounts.password}가 비어 있지 않을 때만 뜨고, 이 값은
 * application-dev.yml에만 {@code ${TEST_ACCOUNT_PASSWORD:}}로 정의된다 — local·prod yml에는 키 자체가
 * 없어 절대 뜨지 않는다. 비밀번호는 코드·yml에 두지 않고 배포 Secret으로 받는다.
 *
 * <p>본인인증(PortOne)을 건너뛰고 {@link SignupService#createVerifiedAccount}를 직접 부른다 —
 * 보상 트랜잭션(member 실패 시 계정 삭제)은 회원가입과 같은 코드를 탄다. 이미 있는 이메일은 건너뛰어
 * 재배포·재시작해도 중복 생성되지 않는다.
 */
@Slf4j
@Component
@ConditionalOnExpression("!'${test-accounts.password:}'.isBlank()")
public class QaTestAccountSeeder implements ApplicationRunner {

    /** member-service가 요구하는 필수 약관(TermsCatalog). */
    private static final List<String> REQUIRED_TERMS = List.of("SERVICE_USE", "PRIVACY", "AGE_OVER_14");

    static final List<TestAccount> ACCOUNTS = List.of(
            new TestAccount("qa-seller@infrastudy.store", "QA판매자", "01000000001", "QA판매자"),
            new TestAccount("qa-supporter@infrastudy.store", "QA서포터", "01000000002", "QA서포터"));

    private final SignupService signupService;
    private final AccountRepository accountRepository;
    private final String password;

    public QaTestAccountSeeder(SignupService signupService, AccountRepository accountRepository,
                               @Value("${test-accounts.password}") String password) {
        this.signupService = signupService;
        this.accountRepository = accountRepository;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (TestAccount account : ACCOUNTS) {
            if (accountRepository.findByEmail(account.email()).isPresent()) {
                continue;
            }
            try {
                signupService.createVerifiedAccount(account.email(), password, account.name(),
                        account.phoneNumber(), account.nickname(), REQUIRED_TERMS, null);
                log.info("QA 테스트 계정 생성 email={}", account.email());
            } catch (RuntimeException e) {
                // ponytail: member-service가 아직 안 떠 있으면 실패한다(계정은 보상 삭제됨). 기동은 막지 않고
                // 다음 재시작 때 다시 시도한다. 배포 순서 때문에 자주 빠지면 재시도 스케줄러로 옮긴다.
                log.warn("QA 테스트 계정 생성 실패, 다음 기동 때 재시도 email={}", account.email(), e);
            }
        }
    }

    record TestAccount(String email, String name, String phoneNumber, String nickname) {
    }
}
