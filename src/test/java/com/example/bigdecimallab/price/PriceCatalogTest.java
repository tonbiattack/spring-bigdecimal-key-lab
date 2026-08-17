package com.example.bigdecimallab.price;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PriceCatalogTest {
    @Autowired
    PriceCatalog priceCatalog;

    @Test
    void numerically_equal_amounts_should_find_the_same_product() {
        String product = priceCatalog.findProduct(new BigDecimal("1.00"));

        assertThat(product)
                .as("1.0と1.00は金額として同じなので同じ商品を返すはず")
                .isEqualTo("coffee");
    }

    @Test
    void compareTo_shows_numeric_equality_but_equals_does_not() {
        BigDecimal onePointZero = new BigDecimal("1.0");
        BigDecimal onePointZeroZero = new BigDecimal("1.00");

        assertThat(onePointZero.compareTo(onePointZeroZero)).isZero();
        assertThat(onePointZero.equals(onePointZeroZero)).isFalse();
    }
}
