package com.example.profit.domain;

/**
 * 수수료 계산 전략. 계산기는 구현체의 종류를 알지 못한다.
 */
public sealed interface FeeStrategy permits TieredFeeStrategy, FlatFeeStrategy {

    /**
     * @param amount 거래 완료 금액 (1원 이상)
     * @return 총수익과 내역 (appliedPolicyId는 비어 있음)
     * @throws IllegalArgumentException 금액이 0 이하일 때
     */
    ProfitBreakdown calculate(Money amount);

    static void requirePositive(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new IllegalArgumentException("거래 완료 금액은 1원 이상이어야 합니다: " + amount);
        }
    }
}
