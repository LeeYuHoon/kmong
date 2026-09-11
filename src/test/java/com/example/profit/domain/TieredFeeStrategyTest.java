package com.example.profit.domain;

import com.example.profit.support.Policies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TieredFeeStrategyTest {

    private final TieredFeeStrategy standard = Policies.standardTiers();

    @Nested
    @DisplayName("기준 요율표 기대값")
    class ExpectedTable {

        @ParameterizedTest(name = "{0}원 → 1구간 {1}, 2구간 {2}, 3구간 {3}, 총수익 {4}")
        @CsvSource({
                "400000,   320000,      0,       0,  320000",
                "950000,   400000, 405000,       0,  805000",
                "2000000,  400000, 450000,  950000, 1800000",
                "5000000,  400000, 450000, 3800000, 4650000",
        })
        void calculatesTotalAndEachTier(long amount, long tier1, long tier2, long tier3, long total) {
            ProfitBreakdown result = standard.calculate(Money.of(amount));

            assertThat(result.total()).isEqualTo(Money.of(total));
            assertThat(result.lines()).extracting(TierResult::profit)
                    .containsExactly(Money.of(tier1), Money.of(tier2), Money.of(tier3));
        }

        @Test
        @DisplayName("내역에는 모든 구간 행이 라벨과 함께 남는다 (0원 구간 포함)")
        void keepsZeroRows() {
            ProfitBreakdown result = standard.calculate(Money.of(400_000));

            assertThat(result.lines()).hasSize(3);
            assertThat(result.lines()).extracting(TierResult::label)
                    .containsExactly("1원 ~ 500,000원", "500,001원 ~ 1,000,000원", "1,000,001원 ~");
            assertThat(result.lines()).extracting(TierResult::amount)
                    .containsExactly(Money.of(400_000), Money.ZERO, Money.ZERO);
            assertThat(result.lines()).extracting(TierResult::rate)
                    .containsExactly(Rate.of(20), Rate.of(10), Rate.of(5));
            assertThat(result.appliedPolicyId()).isNull();
        }
    }

    @Nested
    @DisplayName("경계값")
    class Boundaries {

        @Test
        void amount500000_isEntirelyInTier1() {
            ProfitBreakdown result = standard.calculate(Money.of(500_000));
            assertThat(tierAmounts(result)).containsExactly(500_000L, 0L, 0L);
            assertThat(result.total()).isEqualTo(Money.of(400_000));
        }

        @Test
        void amount500001_puts1WonInTier2() {
            ProfitBreakdown result = standard.calculate(Money.of(500_001));
            assertThat(tierAmounts(result)).containsExactly(500_000L, 1L, 0L);
            // 1원 × 90% = 0.9 → 절사 0원
            assertThat(tierProfits(result)).containsExactly(400_000L, 0L, 0L);
            assertThat(result.total()).isEqualTo(Money.of(400_000));
        }

        @Test
        void amount1000000_fillsTier2() {
            ProfitBreakdown result = standard.calculate(Money.of(1_000_000));
            assertThat(tierAmounts(result)).containsExactly(500_000L, 500_000L, 0L);
            assertThat(tierProfits(result)).containsExactly(400_000L, 450_000L, 0L);
            assertThat(result.total()).isEqualTo(Money.of(850_000));
        }

        @Test
        void amount1000001_puts1WonInTier3() {
            ProfitBreakdown result = standard.calculate(Money.of(1_000_001));
            assertThat(tierAmounts(result)).containsExactly(500_000L, 500_000L, 1L);
            // 1원 × 95% = 0.95 → 절사 0원
            assertThat(tierProfits(result)).containsExactly(400_000L, 450_000L, 0L);
            assertThat(result.total()).isEqualTo(Money.of(850_000));
        }
    }

    @Test
    @DisplayName("절사: 333,333원 → 1구간 266,666원 (266,666.4 절사)")
    void truncatesToWon() {
        ProfitBreakdown result = standard.calculate(Money.of(333_333));

        assertThat(result.lines().getFirst().profit()).isEqualTo(Money.of(266_666));
        assertThat(result.total()).isEqualTo(Money.of(266_666));
    }

    @Test
    @DisplayName("4구간 정책(500,001~750,000원 15% 추가)으로 950,000원 계산 — 계산 로직 수정 없음")
    void fourTierPolicy() {
        TieredFeeStrategy fourTiers = TieredFeeStrategy.of(
                Tier.of(1, 500_000, Rate.of(20)),
                Tier.of(500_001, 750_000, Rate.of(15)),
                Tier.of(750_001, 1_000_000, Rate.of(10)),
                Tier.openEnded(1_000_001, Rate.of(5)));

        ProfitBreakdown result = fourTiers.calculate(Money.of(950_000));

        assertThat(result.lines()).hasSize(4);
        assertThat(tierAmounts(result)).containsExactly(500_000L, 250_000L, 200_000L, 0L);
        assertThat(tierProfits(result)).containsExactly(400_000L, 212_500L, 180_000L, 0L);
        assertThat(result.total()).isEqualTo(Money.of(792_500));
        assertThat(result.lines().get(1).label()).isEqualTo("500,001원 ~ 750,000원");
    }

    @Test
    @DisplayName("소수 요율(18.5%)도 BigDecimal로 정확히 계산된다")
    void decimalRate() {
        TieredFeeStrategy strategy = TieredFeeStrategy.of(Tier.openEnded(1, Rate.of("18.5")));
        // 333,333 × 81.5 / 100 = 271,666.395 → 271,666
        assertThat(strategy.calculate(Money.of(333_333)).total()).isEqualTo(Money.of(271_666));
    }

    @Nested
    @DisplayName("금액 검증")
    class AmountValidation {

        @Test
        void zeroAmountIsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> standard.calculate(Money.ZERO));
        }

        @Test
        void negativeMoneyCannotBeCreated() {
            assertThatIllegalArgumentException().isThrownBy(() -> Money.of(-1));
        }
    }

    @Nested
    @DisplayName("생성 실패")
    class ConstructionFailures {

        @Test
        void empty() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new TieredFeeStrategy(List.of()))
                    .withMessageContaining("1개 이상");
        }

        @Test
        void gapBetweenTiers() {
            assertThatIllegalArgumentException().isThrownBy(() -> TieredFeeStrategy.of(
                            Tier.of(1, 500_000, Rate.of(20)),
                            Tier.of(500_002, 1_000_000, Rate.of(10)),
                            Tier.openEnded(1_000_001, Rate.of(5))))
                    .withMessageContaining("빈틈");
        }

        @Test
        void overlappingTiers() {
            assertThatIllegalArgumentException().isThrownBy(() -> TieredFeeStrategy.of(
                            Tier.of(1, 500_000, Rate.of(20)),
                            Tier.of(500_000, 1_000_000, Rate.of(10)),
                            Tier.openEnded(1_000_001, Rate.of(5))))
                    .withMessageContaining("겹침");
        }

        @Test
        void firstTierDoesNotStartAtOne() {
            assertThatIllegalArgumentException().isThrownBy(() -> TieredFeeStrategy.of(
                            Tier.of(2, 500_000, Rate.of(20)),
                            Tier.openEnded(500_001, Rate.of(10))))
                    .withMessageContaining("1원부터");
        }

        @Test
        void lastTierMustBeOpenEnded() {
            assertThatIllegalArgumentException().isThrownBy(() -> TieredFeeStrategy.of(
                            Tier.of(1, 500_000, Rate.of(20)),
                            Tier.of(500_001, 1_000_000, Rate.of(10))))
                    .withMessageContaining("마지막 구간");
        }

        @Test
        void middleTierMustNotBeOpenEnded() {
            assertThatIllegalArgumentException().isThrownBy(() -> TieredFeeStrategy.of(
                            Tier.openEnded(1, Rate.of(20)),
                            Tier.openEnded(500_001, Rate.of(10))))
                    .withMessageContaining("상한이 있어야");
        }

        @Test
        void tierFromGreaterThanTo() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Tier.of(500_000, 1, Rate.of(20)));
        }
    }

    private static List<Long> tierAmounts(ProfitBreakdown result) {
        return result.lines().stream().map(l -> l.amount().won()).toList();
    }

    private static List<Long> tierProfits(ProfitBreakdown result) {
        return result.lines().stream().map(l -> l.profit().won()).toList();
    }
}
