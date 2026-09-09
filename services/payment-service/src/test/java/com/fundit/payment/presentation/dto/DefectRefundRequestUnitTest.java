package com.fundit.payment.presentation.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefectRefundRequestUnitTest {

    @Test
    void 하자유형을_reasonDetail_앞에_붙인다() {
        var request = new DefectRefundRequest(1024L, DefectRefundRequest.DefectType.DAMAGED, "파손", List.of("url"));
        assertThat(request.toReasonDetail()).isEqualTo("[DAMAGED] 파손");
    }

    @Test
    void reasonDetail이_없으면_태그만_남긴다() {
        var request = new DefectRefundRequest(1024L, DefectRefundRequest.DefectType.DEFECTIVE, null, List.of("url"));
        assertThat(request.toReasonDetail()).isEqualTo("[DEFECTIVE] ");
    }
}
