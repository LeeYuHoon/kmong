package com.example.profit.policy;

import com.example.profit.domain.FeePolicy;
import com.example.profit.domain.FlatFeeStrategy;
import com.example.profit.domain.Rate;
import com.example.profit.support.Policies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FeePolicyValidatorTest {

    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final FeePolicy DEFAULT_V1 = FeePolicy.defaultPolicy("default-v1", JAN_1, Policies.standardTiers());

    @Test
    void validRepositoryPasses() {
        assertThatCode(() -> FeePolicyValidator.validate(List.of(
                DEFAULT_V1,
                FeePolicy.defaultPolicy("default-v2", LocalDate.of(2026, 10, 1), Policies.tiersWithFirstTier18()),
                FeePolicy.categoryPolicy("FIXED_10-v1", "FIXED_10", JAN_1, LocalDate.of(2026, 6, 30),
                        FlatFeeStrategy.of(Rate.of(10))),
                FeePolicy.categoryPolicy("FIXED_10-v2", "FIXED_10", LocalDate.of(2026, 7, 1), null,
                        FlatFeeStrategy.of(Rate.of(10)))))
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 카테고리에 같은 effectiveFrom 중복 → 실패")
    void duplicateEffectiveFromInSameCategory() {
        assertThatIllegalArgumentException().isThrownBy(() -> FeePolicyValidator.validate(List.of(
                DEFAULT_V1,
                FeePolicy.categoryPolicy("c-1", "FIXED_10", JAN_1, null, FlatFeeStrategy.of(Rate.of(10))),
                FeePolicy.categoryPolicy("c-2", "FIXED_10", JAN_1, null, FlatFeeStrategy.of(Rate.of(12))))))
                .withMessageContaining("중복");
    }

    @Test
    @DisplayName("기본 정책끼리 같은 effectiveFrom 중복 → 실패")
    void duplicateEffectiveFromInDefault() {
        assertThatIllegalArgumentException().isThrownBy(() -> FeePolicyValidator.validate(List.of(
                DEFAULT_V1,
                FeePolicy.defaultPolicy("default-dup", JAN_1, Policies.tiersWithFirstTier18()))))
                .withMessageContaining("중복");
    }

    @Test
    @DisplayName("기본 정책에 effectiveTo → 실패")
    void defaultPolicyWithEffectiveTo() {
        assertThatIllegalArgumentException().isThrownBy(() -> FeePolicyValidator.validate(List.of(
                new FeePolicy("default-bad", null, JAN_1, LocalDate.of(2026, 12, 31), Policies.standardTiers()))))
                .withMessageContaining("종료일");
    }

    @Test
    @DisplayName("기본 정책 없음 → 실패")
    void noDefaultPolicy() {
        assertThatIllegalArgumentException().isThrownBy(() -> FeePolicyValidator.validate(List.of(
                FeePolicy.categoryPolicy("c-1", "FIXED_10", JAN_1, null, FlatFeeStrategy.of(Rate.of(10))))))
                .withMessageContaining("기본 정책");
        assertThatIllegalArgumentException().isThrownBy(() -> FeePolicyValidator.validate(List.of()));
    }

    @Test
    @DisplayName("effectiveFrom > effectiveTo → 실패")
    void effectiveFromAfterEffectiveTo() {
        assertThatIllegalArgumentException().isThrownBy(() -> FeePolicyValidator.validate(List.of(
                DEFAULT_V1,
                FeePolicy.categoryPolicy("c-1", "FIXED_10", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 31),
                        FlatFeeStrategy.of(Rate.of(10))))))
                .withMessageContaining("종료일보다");
    }

    @Test
    @DisplayName("정책 id 중복 → 실패")
    void duplicateId() {
        assertThatIllegalArgumentException().isThrownBy(() -> FeePolicyValidator.validate(List.of(
                DEFAULT_V1,
                FeePolicy.defaultPolicy("default-v1", LocalDate.of(2026, 10, 1), Policies.tiersWithFirstTier18()))))
                .withMessageContaining("id");
    }

    @Test
    @DisplayName("저장소 add()가 정합성을 깨면 예외를 던지고 저장소는 변하지 않는다")
    void repositoryAddValidates() {
        InMemoryFeePolicyRepository repository = InMemoryFeePolicyRepository.of(DEFAULT_V1);

        assertThatIllegalArgumentException().isThrownBy(() ->
                repository.add(FeePolicy.defaultPolicy("default-dup", JAN_1, Policies.tiersWithFirstTier18())));
        assertThatCode(() -> FeePolicyValidator.validate(repository.findAll())).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(repository.findAll()).containsExactly(DEFAULT_V1);
    }
}
