package com.example.profit.domain;

import java.util.Objects;

/**
 * 누진 구간 하나. {@code to == null}이면 상한이 없는 마지막 구간이다.
 * 구간 경계는 모두 포함(inclusive)이다.
 */
public record Tier(Money from, Money to, Rate rate) {

    public Tier {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(rate, "rate");
        if (!from.isPositive()) {
            throw new IllegalArgumentException("구간 시작 금액은 1원 이상이어야 합니다: " + from);
        }
        if (to != null && from.compareTo(to) > 0) {
            throw new IllegalArgumentException(
                    "구간 시작 금액이 종료 금액보다 큽니다: " + from + " > " + to);
        }
    }

    public static Tier of(long from, long to, Rate rate) {
        return new Tier(Money.of(from), Money.of(to), rate);
    }

    public static Tier openEnded(long from, Rate rate) {
        return new Tier(Money.of(from), null, rate);
    }

    public boolean isOpenEnded() {
        return to == null;
    }

    /**
     * 주어진 총액 중 이 구간에 해당하는 금액.
     * {@code max(0, min(amount, to) - from + 1)}, to가 없으면 {@code amount - from + 1}.
     */
    public Money amountWithin(Money amount) {
        long upper = to == null ? amount.won() : Math.min(amount.won(), to.won());
        long portion = upper - from.won() + 1;
        return Money.of(Math.max(0, portion));
    }

    /** 예: "1원 ~ 500,000원", 마지막 구간은 "1,000,001원 ~" */
    public String label() {
        return to == null
                ? from.format() + " ~"
                : from.format() + " ~ " + to.format();
    }
}
