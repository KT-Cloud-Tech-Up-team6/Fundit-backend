package com.fundit.payment.domain.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentMethodUnitTest {

    @Test
    void 토스_한글_결제수단을_매핑한다() {
        assertThat(PaymentMethod.fromTossMethod(null)).isNull();
        assertThat(PaymentMethod.fromTossMethod("카드")).isEqualTo(PaymentMethod.CARD);
        assertThat(PaymentMethod.fromTossMethod("가상계좌")).isEqualTo(PaymentMethod.VIRTUAL_ACCOUNT);
        assertThat(PaymentMethod.fromTossMethod("계좌이체")).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(PaymentMethod.fromTossMethod("간편결제")).isEqualTo(PaymentMethod.EASY_PAY);
    }

    @Test
    void 알_수_없는_결제수단이면_예외가_발생한다() {
        assertThatThrownBy(() -> PaymentMethod.fromTossMethod("상품권"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 토스 결제수단");
    }
}
