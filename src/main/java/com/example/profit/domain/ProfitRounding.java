package com.example.profit.domain;

import java.math.RoundingMode;

/**
 * 수익 계산 시 사용하는 원 단위 라운딩 규칙.
 * 정책상 절사(DOWN)를 사용하며, 규칙이 바뀌면 이 상수 하나만 교체한다.
 */
public final class ProfitRounding {

    public static final RoundingMode MODE = RoundingMode.DOWN;

    private ProfitRounding() {
    }
}
