package com.fundit.member.infrastructure.terms;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 약관 원문은 DB 테이블 없이 코드 내 정적 데이터로 관리한다(사용자 확인, 2026-09-03) —
 * 자주 바뀌지 않는 공개 데이터라 배포로 갱신해도 충분하다고 판단.
 * content는 실제 법무 검수된 약관 전문이 아닌 자리표시자(placeholder)이며,
 * 운영팀이 확정한 원문으로 교체가 필요하다.
 */
@Component
public class TermsCatalog {

    public record Terms(String code, String title, String content, boolean required, String version) {
    }

    private static final List<Terms> TERMS = List.of(
            new Terms("SERVICE_USE", "서비스 이용약관", "(약관 전문 — 운영팀 제공 예정)", true, "1.0"),
            new Terms("PRIVACY", "개인정보 처리방침", "(약관 전문 — 운영팀 제공 예정)", true, "1.0"),
            new Terms("AGE_OVER_14", "만 14세 이상입니다", "(약관 전문 — 운영팀 제공 예정)", true, "1.0"),
            new Terms("MARKETING", "마케팅 정보 수신 동의", "(약관 전문 — 운영팀 제공 예정)", false, "1.0"),
            new Terms("AI_PERSONALIZATION", "AI 개인화 추천 활용 동의", "(약관 전문 — 운영팀 제공 예정)", false, "1.0")
    );

    public List<Terms> findAll() {
        return TERMS;
    }
}
