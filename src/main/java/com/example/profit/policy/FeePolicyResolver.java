package com.example.profit.policy;

import com.example.profit.domain.FeePolicy;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * 기준일과 카테고리로 적용할 정책 버전을 고른다.
 * <ol>
 *   <li>categoryId가 있으면 해당 카테고리 정책 중 기준일을 포함하는(effectiveFrom <= date <= effectiveTo) 것 가운데
 *       effectiveFrom이 가장 늦은 것</li>
 *   <li>없으면 기본 정책 중 effectiveFrom <= date 인 것 가운데 가장 늦은 것
 *       (기본 정책의 종료일은 다음 버전의 시행일로 암묵 결정)</li>
 *   <li>둘 다 없으면 {@link NoApplicablePolicyException}</li>
 * </ol>
 */
public final class FeePolicyResolver {

    private static final Comparator<FeePolicy> BY_EFFECTIVE_FROM =
            Comparator.comparing(FeePolicy::effectiveFrom);

    private final FeePolicyRepository repository;

    public FeePolicyResolver(FeePolicyRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public FeePolicy resolve(LocalDate date, String categoryId) {
        Objects.requireNonNull(date, "date");
        return resolveCategory(date, categoryId)
                .or(() -> resolveDefault(date))
                .orElseThrow(() -> new NoApplicablePolicyException(date, categoryId));
    }

    private Optional<FeePolicy> resolveCategory(LocalDate date, String categoryId) {
        if (categoryId == null) {
            return Optional.empty();
        }
        return repository.findAll().stream()
                .filter(p -> categoryId.equals(p.categoryId()))
                .filter(p -> p.covers(date))
                .max(BY_EFFECTIVE_FROM);
    }

    private Optional<FeePolicy> resolveDefault(LocalDate date) {
        return repository.findAll().stream()
                .filter(FeePolicy::isDefault)
                .filter(p -> p.isStartedBy(date))
                .max(BY_EFFECTIVE_FROM);
    }
}
