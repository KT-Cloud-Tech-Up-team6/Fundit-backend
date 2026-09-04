package com.fundit.member.application.terms;

import com.fundit.member.infrastructure.terms.TermsCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TermsServiceUnitTest {

    private final TermsService termsService = new TermsService(new TermsCatalog());

    @Test
    void 약관_목록을_그대로_반환한다() {
        // when
        var result = termsService.getTerms();

        // then
        assertThat(result).extracting(TermsCatalog.Terms::code)
                .contains("SERVICE_USE", "PRIVACY", "AGE_OVER_14", "MARKETING", "AI_PERSONALIZATION");
    }
}
