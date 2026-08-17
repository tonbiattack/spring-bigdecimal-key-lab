package com.example.bigdecimallab.price;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class PriceCatalog {
    private final Map<BigDecimal, String> prices = new HashMap<>();

    public PriceCatalog() {
        prices.put(normalize(new BigDecimal("1.0")), "coffee");
    }

    public String findProduct(BigDecimal amount) {
        return prices.get(normalize(amount));
    }

    private BigDecimal normalize(BigDecimal amount) {
        return amount.stripTrailingZeros();
    }
}
