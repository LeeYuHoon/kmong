package com.example.profit.domain;

import java.util.List;
import java.util.Objects;

/**
 * 구간과 무관하게 전체 금액에 고정 요율을 적용하는 전략. 내역은 단일 행이다.
 */
public record FlatFeeStrategy(Rate rate) implements FeeStrategy {

    public static final String LABEL = "전체 (고정)";

    public FlatFeeStrategy {
        Objects.requireNonNull(rate, "rate");
    }

    public static FlatFeeStrategy of(Rate rate) {
        return new FlatFeeStrategy(rate);
    }

    @Override
    public ProfitBreakdown calculate(Money amount) {
        FeeStrategy.requirePositive(amount);
        Money profit = rate.profitOf(amount);
        return ProfitBreakdown.of(List.of(new TierResult(LABEL, amount, rate, profit)));
    }
}
