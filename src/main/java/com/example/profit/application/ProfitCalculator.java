package com.example.profit.application;

import com.example.profit.domain.FeePolicy;
import com.example.profit.domain.Money;
import com.example.profit.domain.ProfitBreakdown;
import com.example.profit.domain.Transaction;
import com.example.profit.policy.FeePolicyResolver;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 거래 완료 금액에서 총수익을 계산한다.
 * 요율 확정 기준일은 결제일이며, "오늘"이 필요한 경우 주입된 {@link Clock}을 사용한다.
 */
public final class ProfitCalculator {

    private final Clock clock;
    private final FeePolicyResolver resolver;

    public ProfitCalculator(Clock clock, FeePolicyResolver resolver) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** 거래의 결제일(paidAt) 기준으로 정책을 확정해 계산한다. 이미 결제된 거래는 재계산해도 결과가 같다. */
    public ProfitBreakdown calculateProfitAmount(Transaction tx) {
        Objects.requireNonNull(tx, "tx");
        return calculateProfitAmount(tx.amount(), tx.categoryId(), tx.paidAt());
    }

    /** 주입된 Clock의 오늘 날짜를 결제일로 보고 계산한다 (지금 결제되는 거래). */
    public ProfitBreakdown calculateProfitAmount(Money amount, String categoryId) {
        return calculateProfitAmount(amount, categoryId, LocalDate.now(clock));
    }

    /** 임의의 기준일로 계산한다 (what-if 시뮬레이션, 미래 요율 미리보기 등). */
    public ProfitBreakdown calculateProfitAmount(Money amount, String categoryId, LocalDate asOf) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(asOf, "asOf");
        FeePolicy policy = resolver.resolve(asOf, categoryId);
        return policy.strategy()
                .calculate(amount)
                .withAppliedPolicyId(policy.id());
    }
}
