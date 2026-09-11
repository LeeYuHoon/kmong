package com.example.profit.domain;

import java.math.BigDecimal;

/**
 * 수수료율(퍼센트) 값 객체. 0 이상 100 이하만 허용한다.
 * 소수 요율(예: 18.5%)에 대비해 BigDecimal을 사용한다.
 */
public record Rate(BigDecimal percent) implements Comparable<Rate> {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Rate {
        if (percent == null) {
            throw new IllegalArgumentException("요율은 null일 수 없습니다");
        }
        if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("요율은 0 이상 100 이하여야 합니다: " + percent);
        }
        percent = percent.stripTrailingZeros();
        if (percent.scale() < 0) {
            percent = percent.setScale(0);
        }
    }

    public static Rate of(long percent) {
        return new Rate(BigDecimal.valueOf(percent));
    }

    public static Rate of(String percent) {
        return new Rate(new BigDecimal(percent));
    }

    public static Rate of(BigDecimal percent) {
        return new Rate(percent);
    }

    /**
     * 수수료를 뺀 수익: amount × (100 − 요율) / 100, 원 단위 {@link ProfitRounding#MODE} 적용.
     */
    public Money profitOf(Money amount) {
        BigDecimal profit = BigDecimal.valueOf(amount.won())
                .multiply(HUNDRED.subtract(percent))
                .divide(HUNDRED, 0, ProfitRounding.MODE);
        return Money.of(profit.longValueExact());
    }

    @Override
    public int compareTo(Rate other) {
        return percent.compareTo(other.percent);
    }

    @Override
    public String toString() {
        return percent.toPlainString() + "%";
    }
}
