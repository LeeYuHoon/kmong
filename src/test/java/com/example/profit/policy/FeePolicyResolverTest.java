package com.example.profit.policy;

import com.example.profit.domain.FeePolicy;
import com.example.profit.domain.FlatFeeStrategy;
import com.example.profit.domain.Rate;
import com.example.profit.support.Policies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeePolicyResolverTest {

    private static final FeePolicy V1 = FeePolicy.defaultPolicy(
            "default-v1", LocalDate.of(2026, 1, 1), Policies.standardTiers());
    private static final FeePolicy V2 = FeePolicy.defaultPolicy(
            "default-v2", LocalDate.of(2026, 10, 1), Policies.tiersWithFirstTier18());

    @Test
    @DisplayName("기본 정책 v1(2026-01-01), v2(2026-10-01): 2026-09-15 → v1, 2026-10-01 → v2")
    void picksLatestStartedDefaultPolicy() {
        FeePolicyResolver resolver = new FeePolicyResolver(InMemoryFeePolicyRepository.of(V1, V2));

        assertThat(resolver.resolve(LocalDate.of(2026, 9, 15), null).id()).isEqualTo("default-v1");
        assertThat(resolver.resolve(LocalDate.of(2026, 9, 30), null).id()).isEqualTo("default-v1");
        assertThat(resolver.resolve(LocalDate.of(2026, 10, 1), null).id()).isEqualTo("default-v2");
        assertThat(resolver.resolve(LocalDate.of(2027, 3, 1), null).id()).isEqualTo("default-v2");
    }

    @Test
    @DisplayName("저장 순서와 무관하게 effectiveFrom이 가장 늦은 정책을 고른다")
    void independentOfInsertionOrder() {
        FeePolicyResolver resolver = new FeePolicyResolver(InMemoryFeePolicyRepository.of(V2, V1));

        assertThat(resolver.resolve(LocalDate.of(2026, 10, 1), null).id()).isEqualTo("default-v2");
        assertThat(resolver.resolve(LocalDate.of(2026, 9, 15), null).id()).isEqualTo("default-v1");
    }

    @Test
    @DisplayName("카테고리 정책이 있으면 카테고리 우선, effectiveTo 지난 뒤에는 기본 정책으로 복귀")
    void categoryPolicyTakesPrecedenceWithinItsPeriod() {
        FeePolicy fixed10 = FeePolicy.categoryPolicy("FIXED_10-v1", "FIXED_10",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 8, 31), FlatFeeStrategy.of(Rate.of(10)));
        FeePolicyResolver resolver = new FeePolicyResolver(InMemoryFeePolicyRepository.of(V1, V2, fixed10));

        assertThat(resolver.resolve(LocalDate.of(2026, 2, 28), "FIXED_10").id()).isEqualTo("default-v1");
        assertThat(resolver.resolve(LocalDate.of(2026, 3, 1), "FIXED_10").id()).isEqualTo("FIXED_10-v1");
        assertThat(resolver.resolve(LocalDate.of(2026, 8, 31), "FIXED_10").id()).isEqualTo("FIXED_10-v1");
        assertThat(resolver.resolve(LocalDate.of(2026, 9, 1), "FIXED_10").id()).isEqualTo("default-v1");
        assertThat(resolver.resolve(LocalDate.of(2026, 10, 1), "FIXED_10").id()).isEqualTo("default-v2");
        // 다른 카테고리는 영향 없음
        assertThat(resolver.resolve(LocalDate.of(2026, 5, 1), "OTHER").id()).isEqualTo("default-v1");
    }

    @Test
    @DisplayName("무기한 카테고리 정책은 기본 정책이 바뀌어도 계속 우선한다")
    void openEndedCategoryPolicy() {
        FeePolicy fixed10 = FeePolicy.categoryPolicy("FIXED_10-v1", "FIXED_10",
                LocalDate.of(2026, 3, 1), null, FlatFeeStrategy.of(Rate.of(10)));
        FeePolicyResolver resolver = new FeePolicyResolver(InMemoryFeePolicyRepository.of(V1, V2, fixed10));

        assertThat(resolver.resolve(LocalDate.of(2026, 12, 1), "FIXED_10").id()).isEqualTo("FIXED_10-v1");
    }

    @Test
    @DisplayName("유효 정책 없음 → NoApplicablePolicyException")
    void noPolicyBeforeFirstEffectiveFrom() {
        FeePolicyResolver resolver = new FeePolicyResolver(InMemoryFeePolicyRepository.of(V1, V2));

        assertThatThrownBy(() -> resolver.resolve(LocalDate.of(2025, 12, 31), null))
                .isInstanceOf(NoApplicablePolicyException.class)
                .hasMessageContaining("2025-12-31");
        assertThatThrownBy(() -> resolver.resolve(LocalDate.of(2025, 12, 31), "FIXED_10"))
                .isInstanceOf(NoApplicablePolicyException.class)
                .hasMessageContaining("FIXED_10");
    }
}
