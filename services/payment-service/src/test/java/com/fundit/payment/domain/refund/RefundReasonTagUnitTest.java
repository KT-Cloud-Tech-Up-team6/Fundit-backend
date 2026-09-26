package com.fundit.payment.domain.refund;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefundReasonTagUnitTest {

    @Test
    void 사유_유형을_상세_앞에_태그로_붙인다() {
        // given & when
        String formatted = RefundReasonTag.format(ExchangeReason.CHANGE_OF_MIND, "색상이 달라요");

        // then
        assertThat(formatted).isEqualTo("[CHANGE_OF_MIND] 색상이 달라요");
    }

    @Test
    void 태그가_붙은_저장값은_유형과_상세로_나뉜다() {
        // given & when
        RefundReasonTag.Parsed parsed = RefundReasonTag.parse("[DAMAGED] 파손됨");

        // then
        assertThat(parsed.reasonType()).isEqualTo("DAMAGED");
        assertThat(parsed.detail()).isEqualTo("파손됨");
    }

    @Test
    void 상세가_없으면_유형만_남고_상세는_null이다() {
        // given & when
        RefundReasonTag.Parsed parsed = RefundReasonTag.parse(RefundReasonTag.format(DefectType.DEFECTIVE, null));

        // then
        assertThat(parsed.reasonType()).isEqualTo("DEFECTIVE");
        assertThat(parsed.detail()).isNull();
    }

    @Test
    void 태그가_없는_사유는_상세로만_내려간다() {
        // given & when — 발송지연·목표미달처럼 유형이 없는 사유, 태그 도입 이전 데이터
        RefundReasonTag.Parsed parsed = RefundReasonTag.parse("발송이 너무 늦어요");

        // then
        assertThat(parsed.reasonType()).isNull();
        assertThat(parsed.detail()).isEqualTo("발송이 너무 늦어요");
    }

    @Test
    void 구매자가_대괄호로_시작하는_상세를_쓰면_유형으로_오인하지_않는다() {
        // given & when
        RefundReasonTag.Parsed parsed = RefundReasonTag.parse("[중요] 색상이 달라요");

        // then
        assertThat(parsed.reasonType()).isNull();
        assertThat(parsed.detail()).isEqualTo("[중요] 색상이 달라요");
    }

    @Test
    void 사유가_없으면_유형과_상세_모두_null이다() {
        // given & when
        RefundReasonTag.Parsed parsed = RefundReasonTag.parse(null);

        // then
        assertThat(parsed.reasonType()).isNull();
        assertThat(parsed.detail()).isNull();
    }
}
