package com.example.profit.domain;

import java.util.Locale;

/**
 * 원(KRW) 단위 금액 값 객체. 음수를 허용하지 않는다.
 */
public record Money(long won) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    public Money {
        if (won < 0) {
            throw new IllegalArgumentException("금액은 음수일 수 없습니다: " + won);
        }
    }

    public static Money of(long won) {
        return new Money(won);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(won, other.won));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(won, other.won));
    }

    public Money min(Money other) {
        return won <= other.won ? this : other;
    }

    public boolean isPositive() {
        return won > 0;
    }

    public boolean isZero() {
        return won == 0;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(won, other.won);
    }

    /** 예: 1,000,000원 */
    public String format() {
        return String.format(Locale.ROOT, "%,d원", won);
    }

    @Override
    public String toString() {
        return format();
    }
}
