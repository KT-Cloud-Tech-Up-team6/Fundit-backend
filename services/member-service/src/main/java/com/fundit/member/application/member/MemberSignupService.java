package com.fundit.member.application.member;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.infrastructure.persistence.address.AddressJpaEntity;
import com.fundit.member.infrastructure.persistence.address.AddressJpaRepository;
import com.fundit.member.infrastructure.persistence.member.MemberJpaEntity;
import com.fundit.member.infrastructure.persistence.member.MemberJpaRepository;
import com.fundit.member.infrastructure.persistence.termsagreement.TermsAgreementJpaEntity;
import com.fundit.member.infrastructure.persistence.termsagreement.TermsAgreementJpaRepository;
import com.fundit.member.infrastructure.terms.TermsCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * auth-service의 POST /api/v1/auth/signup이 동기 호출하는 내부 전용 유스케이스.
 * 실제 요청 계약은 문서가 아니라 auth-service의 MemberServiceClient.CreateMemberProfileCommand
 * (List&lt;String&gt; agreedTerms, email 포함)를 기준으로 맞춘다 — email은 auth-service 소관이라
 * 받기만 하고 저장하지 않는다. 실패 시 보상 트랜잭션은 auth-service 책임이라
 * 이 서비스는 자체 롤백 로직을 두지 않고 예외만 던진다.
 */
@Service
@RequiredArgsConstructor
public class MemberSignupService {

    private final MemberJpaRepository memberJpaRepository;
    private final TermsAgreementJpaRepository termsAgreementJpaRepository;
    private final AddressJpaRepository addressJpaRepository;
    private final TermsCatalog termsCatalog;

    @Transactional
    public SignupResult signup(SignupCommand command) {
        if (memberJpaRepository.existsById(command.accountId())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "이미 생성된 회원 프로필입니다.");
        }

        List<String> requiredCodes = termsCatalog.findAll().stream()
                .filter(TermsCatalog.Terms::required)
                .map(TermsCatalog.Terms::code)
                .toList();
        if (!command.agreedTerms().containsAll(requiredCodes)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "필수 약관에 동의해야 합니다.");
        }

        MemberJpaEntity member = memberJpaRepository.save(MemberJpaEntity.builder()
                .id(command.accountId())
                .name(command.name())
                .phoneNumber(command.phoneNumber())
                .build());

        List<TermsAgreementJpaEntity> agreements = command.agreedTerms().stream()
                .map(this::findTerms)
                .map(terms -> TermsAgreementJpaEntity.builder()
                        .memberId(member.getId())
                        .termsType(terms.code())
                        .termsVersion(terms.version())
                        .agreed(true)
                        .build())
                .toList();
        termsAgreementJpaRepository.saveAll(agreements);

        if (command.address() != null && !command.address().isEmpty()) {
            addressJpaRepository.save(toAddressEntity(member.getId(), command.address()));
        }

        return new SignupResult(member.getId(), member.getCreatedAt());
    }

    private TermsCatalog.Terms findTerms(String code) {
        return termsCatalog.findAll().stream()
                .filter(terms -> terms.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT, "존재하지 않는 약관 코드입니다: " + code));
    }

    private AddressJpaEntity toAddressEntity(UUID memberId, Map<String, Object> address) {
        return AddressJpaEntity.builder()
                .memberId(memberId)
                .recipientName((String) address.get("recipientName"))
                .phoneNumber((String) address.get("phoneNumber"))
                .zipcode((String) address.get("zipcode"))
                .addressLine1((String) address.get("addressLine1"))
                .addressLine2((String) address.get("addressLine2"))
                .isDefault(Boolean.TRUE.equals(address.get("isDefault")))
                .build();
    }

    public record SignupCommand(
            UUID accountId,
            String name,
            String phoneNumber,
            List<String> agreedTerms,
            Map<String, Object> address
    ) {
    }

    public record SignupResult(UUID memberId, Instant createdAt) {
    }
}
