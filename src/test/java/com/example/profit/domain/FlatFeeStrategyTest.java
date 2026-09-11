package com.example.profit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FlatFeeStrategyTest {

    private final FlatFeeStrategy flat10 = FlatFeeStrategy.of(Rate.of(10));

    @Test
    @DisplayName("고정 10%: 950,000원 → 855,000원, 내역 1행")
    void flatTenPercent() {
        ProfitBreakdown result = flat10.calculate(Money.of(950_000));

        assertThat(result.total()).isEqualTo(Money.of(855_000));
        assertThat(result.lines()).hasSize(1);
        TierResult line = result.lines().getFirst();
        assertThat(line.amount()).isEqualTo(Money.of(950_000));
        assertThat(line.rate()).isEqualTo(Rate.of(10));
        assertThat(line.profit()).isEqualTo(Money.of(855_000));
    }

    @Test
    @DisplayName("구간과 무관하게 큰 금액도 단일 요율")
    void largeAmount() {
        assertThat(flat10.calculate(Money.of(5_000_000)).total()).isEqualTo(Money.of(4_500_000));
    }

    @Test
    @DisplayName("절사: 1원 × 90% = 0.9 → 0원")
    void truncates() {
        assertThat(flat10.calculate(Money.of(1)).total()).isEqualTo(Money.ZERO);
    }

    @Test
    void zeroAmountIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> flat10.calculate(Money.ZERO));
    }
}
