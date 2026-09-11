package com.example.profit.domain;

import java.util.List;
import java.util.Objects;

/**
 * 총수익과 구간별 내역. {@code appliedPolicyId}는 어떤 정책 버전으로 계산되었는지 추적하기 위한 값으로,
 * 전략 단독으로 계산한 경우에는 null이다.
 */
public record ProfitBreakdown(Money total, List<TierResult> lines, String appliedPolicyId) {

    public ProfitBreakdown {
        Objects.requireNonNull(total, "total");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
    }

    public static ProfitBreakdown of(List<TierResult> lines) {
        Money total = lines.stream().map(TierResult::profit).reduce(Money.ZERO, Money::plus);
        return new ProfitBreakdown(total, lines, null);
    }

    public ProfitBreakdown withAppliedPolicyId(String policyId) {
        return new ProfitBreakdown(total, lines, policyId);
    }
}
