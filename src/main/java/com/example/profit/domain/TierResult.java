package com.example.profit.domain;

import java.util.Objects;

/**
 * 계산 내역의 한 행. 구간 라벨, 해당 구간 금액, 적용 요율, 구간 수익.
 */
public record TierResult(String label, Money amount, Rate rate, Money profit) {

    public TierResult {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(profit, "profit");
    }
}
