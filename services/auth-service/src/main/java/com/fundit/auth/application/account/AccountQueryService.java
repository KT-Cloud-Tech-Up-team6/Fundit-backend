package com.fundit.auth.application.account;

import com.fundit.auth.domain.account.AccountRepository;
import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 로그인한 본인 계정 조회. 이메일은 auth-service만 갖고 있어(member에는 저장하지 않는다) 마이페이지가 여기서 가져간다.
 * 복호화는 {@code AccountMapper}가 이미 해서 넘겨준다.
 */
@Service
@RequiredArgsConstructor
public class AccountQueryService {

    private final AccountRepository accountRepository;

    public String getEmail(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND))
                .getEmail();
    }
}
