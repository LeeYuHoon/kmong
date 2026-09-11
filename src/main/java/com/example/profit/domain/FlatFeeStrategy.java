package com.example.profit.domain;

import java.util.List;
import java.util.Objects;

/**
 * 구간과 무관하게 전체 금액에 고정 요율을 적용하는 전략. 내역은 단일 행이다.
 */
public final class FlatFeeStrategy implements FeeStrategy {

    public static final String LABEL = "전체 (고정)";

    private final Rate rate;

    public FlatFeeStrategy(Rate rate) {
        this.rate = Objects.requireNonNull(rate, "rate");
    }

    public static FlatFeeStrategy of(Rate rate) {
        return new FlatFeeStrategy(rate);
    }

    public Rate rate() {
        return rate;
    }

    @Override
    public ProfitBreakdown calculate(Money amount) {
        FeeStrategy.requirePositive(amount);
        Money profit = rate.profitOf(amount);
        return ProfitBreakdown.of(List.of(new TierResult(LABEL, amount, rate, profit)));
    }
}
