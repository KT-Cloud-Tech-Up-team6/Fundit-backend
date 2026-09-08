package com.fundit.project.application.ai;

import com.fundit.project.domain.aifundingstory.FundingStoryAnswer;
import com.fundit.project.domain.aifundingstory.FundingStoryResult;

import java.util.List;

/**
 * 외부 AI 서비스 연동 포트(PROJECT-011, PROJECT-012). API Key 등은 이 포트를 구현하는
 * 실제 어댑터에서만 다루고 애플리케이션 계층은 결과 계약만 안다(security.md S7).
 *
 * 이 슬라이스 시점엔 실제 외부 AI 연동이 없어 즉시 결과를 만들어 반환하는
 * {@code MockFundingStoryAiClient}만 둔다 — 실제 연동 시 이 인터페이스의 구현체만 교체하면 된다.
 * 실패 시(외부 서비스 오류) {@link com.fundit.common.error.DependencyFailureException}을 던진다.
 */
public interface FundingStoryAiClient {

    FundingStoryResult generate(String productDescription, List<String> productImageUrls, List<FundingStoryAnswer> answers);
}
