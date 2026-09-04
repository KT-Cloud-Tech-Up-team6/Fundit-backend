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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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

        MemberJpaEntity member;
        try {
            member = memberJpaRepository.save(MemberJpaEntity.builder()
                    .id(command.accountId())
                    .name(command.name())
                    .phoneNumber(command.phoneNumber())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // existsById 체크 이후 동시 요청으로 인한 PK 충돌(TOCTOU) — 유니크 제약 위반을
            // 500 대신 동일한 409로 정리한다.
            throw new BusinessException(CommonErrorCode.CONFLICT, "이미 생성된 회원 프로필입니다.");
        }

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

        if (command.address() != null && command.address().recipientName() != null) {
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

    private AddressJpaEntity toAddressEntity(UUID memberId, AddressPayload address) {
        return AddressJpaEntity.builder()
                .memberId(memberId)
                .recipientName(address.recipientName())
                .phoneNumber(address.phoneNumber())
                .zipcode(address.zipcode())
                .addressLine1(address.addressLine1())
                .addressLine2(address.addressLine2())
                .isDefault(Boolean.TRUE.equals(address.isDefault()))
                .build();
    }

    public record SignupCommand(
            UUID accountId,
            String name,
            String phoneNumber,
            List<String> agreedTerms,
            AddressPayload address
    ) {
    }

    public record SignupResult(UUID memberId, Instant createdAt) {
    }

    /**
     * auth-service가 보내는 address는 주소 미입력 시 빈 객체({})로 오므로 필드 검증
     * 애노테이션을 붙이지 않는다 — recipientName 존재 여부로 "실제 주소 입력됨"을 판단한다.
     * presentation의 MemberCreateRequest가 이 타입을 그대로 JSON 역직렬화 대상으로 쓴다
     * (Map&lt;String, Object&gt; 원시 캐스팅 제거).
     */
    public record AddressPayload(
            String recipientName,
            String phoneNumber,
            String zipcode,
            String addressLine1,
            String addressLine2,
            Boolean isDefault
    ) {
    }
}
