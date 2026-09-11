package com.example.profit.application;

import com.example.profit.domain.FeePolicy;
import com.example.profit.domain.FlatFeeStrategy;
import com.example.profit.domain.Money;
import com.example.profit.domain.ProfitBreakdown;
import com.example.profit.domain.Rate;
import com.example.profit.domain.TierResult;
import com.example.profit.domain.Transaction;
import com.example.profit.policy.FeePolicyResolver;
import com.example.profit.policy.InMemoryFeePolicyRepository;
import com.example.profit.policy.NoApplicablePolicyException;
import com.example.profit.support.Policies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfitCalculatorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Money AMOUNT_950K = Money.of(950_000);

    private static final FeePolicy V1 = FeePolicy.defaultPolicy(
            "default-v1", LocalDate.of(2026, 1, 1), Policies.standardTiers());
    private static final FeePolicy V2 = FeePolicy.defaultPolicy(
            "default-v2", LocalDate.of(2026, 10, 1), Policies.tiersWithFirstTier18());
    private static final FeePolicy FIXED_10 = FeePolicy.categoryPolicy(
            "FIXED_10-v1", "FIXED_10", LocalDate.of(2026, 1, 1), null, FlatFeeStrategy.of(Rate.of(10)));

    private static Clock fixedAt(LocalDate date) {
        return Clock.fixed(date.atStartOfDay(SEOUL).toInstant(), SEOUL);
    }

    private InMemoryFeePolicyRepository repository;
    private ProfitCalculator calculator;

    @BeforeEach
    void setUp() {
        repository = InMemoryFeePolicyRepository.of(V1);
        calculator = new ProfitCalculator(fixedAt(LocalDate.of(2026, 9, 15)),
                new FeePolicyResolver(repository));
    }

    @Nested
    @DisplayName("시나리오 1: 다음 달부터 1구간 20% → 18%, 지난달 거래 재계산")
    class Scenario1 {

        private final Transaction lastMonth = Transaction.of(950_000, LocalDate.of(2026, 8, 20), null);

        @Test
        @DisplayName("오늘 2026-09-15, v1만 있을 때 지난달(08-20 결제) 950,000원 → 805,000원")
        void beforeRateChange() {
            ProfitBreakdown result = calculator.calculateProfitAmount(lastMonth);

            assertThat(result.total()).isEqualTo(Money.of(805_000));
            assertThat(result.appliedPolicyId()).isEqualTo("default-v1");
        }

        @Test
        @DisplayName("v2(18%, 2026-10-01) 추가 후 같은 거래 재계산 → 여전히 805,000원 (결제일 기준 확정, 소급 없음)")
        void recalculationAfterAddingV2IsUnchanged() {
            ProfitBreakdown before = calculator.calculateProfitAmount(lastMonth);
            repository.add(V2);
            ProfitBreakdown after = calculator.calculateProfitAmount(lastMonth);

            assertThat(after.total()).isEqualTo(Money.of(805_000));
            assertThat(after).isEqualTo(before);
            assertThat(after.appliedPolicyId()).isEqualTo("default-v1");
        }

        @Test
        @DisplayName("2026-10-05 결제 950,000원 → 1구간 410,000원(18%) 적용 → 815,000원")
        void paymentAfterEffectiveDateUsesV2() {
            repository.add(V2);

            ProfitBreakdown result = calculator.calculateProfitAmount(
                    Transaction.of(950_000, LocalDate.of(2026, 10, 5), null));

            assertThat(result.lines()).extracting(TierResult::profit)
                    .containsExactly(Money.of(410_000), Money.of(405_000), Money.ZERO);
            assertThat(result.lines().getFirst().rate()).isEqualTo(Rate.of(18));
            assertThat(result.total()).isEqualTo(Money.of(815_000));
            assertThat(result.appliedPolicyId()).isEqualTo("default-v2");
        }

        @Test
        @DisplayName("시행일 당일(10-01) 결제부터 v2, 전날(09-30) 결제는 v1")
        void effectiveDateBoundary() {
            repository.add(V2);

            assertThat(calculator.calculateProfitAmount(
                    Transaction.of(950_000, LocalDate.of(2026, 9, 30), null)).appliedPolicyId())
                    .isEqualTo("default-v1");
            assertThat(calculator.calculateProfitAmount(
                    Transaction.of(950_000, LocalDate.of(2026, 10, 1), null)).appliedPolicyId())
                    .isEqualTo("default-v2");
        }

        @Test
        @DisplayName("what-if 시뮬레이션: asOf를 10월로 주면 오늘이 9월이어도 v2로 계산")
        void simulationWithAsOf() {
            repository.add(V2);

            ProfitBreakdown result = calculator.calculateProfitAmount(
                    AMOUNT_950K, null, LocalDate.of(2026, 10, 1));

            assertThat(result.total()).isEqualTo(Money.of(815_000));
            assertThat(result.appliedPolicyId()).isEqualTo("default-v2");
        }
    }

    @Nested
    @DisplayName("시나리오 2: 특정 카테고리 고정 10%")
    class Scenario2 {

        @BeforeEach
        void addCategoryPolicy() {
            repository.add(FIXED_10);
        }

        @Test
        @DisplayName("카테고리 FIXED_10 950,000원 → 855,000원, 내역 1행")
        void fixedCategory() {
            ProfitBreakdown result = calculator.calculateProfitAmount(
                    Transaction.of(950_000, LocalDate.of(2026, 8, 20), "FIXED_10"));

            assertThat(result.total()).isEqualTo(Money.of(855_000));
            assertThat(result.lines()).hasSize(1);
            assertThat(result.appliedPolicyId()).isEqualTo("FIXED_10-v1");
        }

        @Test
        @DisplayName("다른 카테고리·카테고리 없음 → 기본 구간제 805,000원")
        void otherCategoriesUseDefault() {
            ProfitBreakdown other = calculator.calculateProfitAmount(
                    Transaction.of(950_000, LocalDate.of(2026, 8, 20), "DESIGN"));
            ProfitBreakdown none = calculator.calculateProfitAmount(
                    Transaction.of(950_000, LocalDate.of(2026, 8, 20), null));

            assertThat(other.total()).isEqualTo(Money.of(805_000));
            assertThat(other.appliedPolicyId()).isEqualTo("default-v1");
            assertThat(none.total()).isEqualTo(Money.of(805_000));
            assertThat(none.appliedPolicyId()).isEqualTo("default-v1");
        }

        @Test
        @DisplayName("카테고리 예외는 기간(effectiveTo)으로 종료할 수 있다")
        void categoryPolicyCanExpire() {
            InMemoryFeePolicyRepository repo = InMemoryFeePolicyRepository.of(V1,
                    FeePolicy.categoryPolicy("FIXED_10-limited", "FIXED_10",
                            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), FlatFeeStrategy.of(Rate.of(10))));
            ProfitCalculator calc = new ProfitCalculator(fixedAt(LocalDate.of(2026, 9, 15)),
                    new FeePolicyResolver(repo));

            assertThat(calc.calculateProfitAmount(Transaction.of(950_000, LocalDate.of(2026, 6, 30), "FIXED_10")).total())
                    .isEqualTo(Money.of(855_000));
            assertThat(calc.calculateProfitAmount(Transaction.of(950_000, LocalDate.of(2026, 7, 1), "FIXED_10")).total())
                    .isEqualTo(Money.of(805_000));
        }
    }

    @Nested
    @DisplayName("calculateProfitAmount(amount, categoryId)는 Clock의 오늘을 기준으로 정책을 고른다")
    class ClockBased {

        @Test
        @DisplayName("오늘 = 2026-09-15 → v1 (805,000원)")
        void septemberClockUsesV1() {
            repository.add(V2);

            ProfitBreakdown result = calculator.calculateProfitAmount(AMOUNT_950K, null);

            assertThat(result.total()).isEqualTo(Money.of(805_000));
            assertThat(result.appliedPolicyId()).isEqualTo("default-v1");
        }

        @Test
        @DisplayName("오늘 = 2026-10-15 로 고정하면 v2 (815,000원)")
        void octoberClockUsesV2() {
            repository.add(V2);
            ProfitCalculator octoberCalculator = new ProfitCalculator(
                    fixedAt(LocalDate.of(2026, 10, 15)), new FeePolicyResolver(repository));

            ProfitBreakdown result = octoberCalculator.calculateProfitAmount(AMOUNT_950K, null);

            assertThat(result.total()).isEqualTo(Money.of(815_000));
            assertThat(result.appliedPolicyId()).isEqualTo("default-v2");
        }

        @Test
        @DisplayName("Clock의 시간대에 따라 '오늘'이 결정된다 (UTC 자정 직전 = 서울 다음날)")
        void clockZoneDeterminesToday() {
            repository.add(V2);
            // 2026-09-30T23:00Z = 서울 2026-10-01T08:00 → v2
            Clock utcClock = Clock.fixed(java.time.Instant.parse("2026-09-30T23:00:00Z"), SEOUL);
            ProfitCalculator calc = new ProfitCalculator(utcClock, new FeePolicyResolver(repository));

            assertThat(calc.calculateProfitAmount(AMOUNT_950K, null).appliedPolicyId()).isEqualTo("default-v2");
        }
    }

    @Test
    @DisplayName("적용 가능한 정책이 없으면 NoApplicablePolicyException")
    void noPolicy() {
        assertThatThrownBy(() -> calculator.calculateProfitAmount(
                Transaction.of(950_000, LocalDate.of(2025, 6, 1), null)))
                .isInstanceOf(NoApplicablePolicyException.class);
    }
}
