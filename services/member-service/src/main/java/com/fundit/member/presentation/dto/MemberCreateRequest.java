package com.fundit.member.presentation.dto;

import com.fundit.member.application.member.MemberSignupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * auth-service의 MemberServiceClient.CreateMemberProfileCommand와 계약을 맞춘다.
 * agreedTerms는 동의한 약관 코드 목록(List&lt;String&gt;) — API 문서의 [{code, agreed}] 형태와
 * 다르니 재개 시 문서도 이 계약으로 정정할 것. email은 auth-service 소관이라 받기만 하고
 * member-service 스키마에는 저장하지 않는다. address는 MemberSignupService.AddressPayload를
 * 그대로 역직렬화 대상으로 써서 Map&lt;String, Object&gt; 원시 캐스팅을 없애고, @Valid로
 * 그 타입의 @CompleteAddress(빈 객체=주소 없음, 부분 입력은 거부)를 함께 적용한다.
 */
public record MemberCreateRequest(
        @NotNull UUID accountId,
        String email,
        @NotBlank String name,
        @NotBlank String phoneNumber,
        @NotEmpty List<String> agreedTerms,
        @Valid MemberSignupService.AddressPayload address
) {
}
