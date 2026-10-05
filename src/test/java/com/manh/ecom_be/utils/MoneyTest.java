package com.manh.ecom_be.utils;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class MoneyTest {
    @Test void decimalArithmeticAndBoundaryAreExact() {
        assertThat(Money.amount(new BigDecimal("0.10").add(new BigDecimal("0.20"))))
                .isEqualByComparingTo("0.30");
        assertThat(Money.amount(Money.MAX)).isEqualTo(Money.MAX);
        assertThat(Money.amount(new BigDecimal("1.000"))).isEqualTo(new BigDecimal("1.00"));
        assertThatThrownBy(() -> Money.amount(Money.MAX.add(new BigDecimal("0.01"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.amount(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"-0.01", "1.001", "10000000.01"})
    void invalidProductPricesAreRejected(String input) {
        assertThatThrownBy(() -> Money.productPrice(new BigDecimal(input)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void discountsRoundPayableAmountHalfUp() {
        assertThat(Money.discount(new BigDecimal("0.05"), BigDecimal.TEN))
                .isEqualTo(new BigDecimal("0.05"));
        assertThat(Money.discount(new BigDecimal("19.99"), new BigDecimal("12.5")))
                .isEqualTo(new BigDecimal("17.49"));
        assertThat(Money.discount(new BigDecimal("19.99"), new BigDecimal("100")))
                .isEqualTo(new BigDecimal("0.00"));
        assertThatThrownBy(() -> Money.discount(BigDecimal.ONE, new BigDecimal("101")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
