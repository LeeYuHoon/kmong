package com.example.profit.policy;

import com.example.profit.domain.FeePolicy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 정책 저장소 전체에 대한 정합성 검증.
 * <ul>
 *   <li>정책 id 중복 금지</li>
 *   <li>같은 categoryId 내 effectiveFrom 중복 금지</li>
 *   <li>기본 정책(categoryId == null)은 effectiveTo를 가질 수 없음</li>
 *   <li>기본 정책이 최소 1개 존재</li>
 *   <li>effectiveTo가 있으면 effectiveFrom <= effectiveTo</li>
 * </ul>
 */
public final class FeePolicyValidator {

    private FeePolicyValidator() {
    }

    public static void validate(List<FeePolicy> policies) {
        if (policies == null) {
            throw new IllegalArgumentException("정책 목록이 null입니다");
        }
        Set<String> ids = new HashSet<>();
        Map<String, Set<LocalDate>> effectiveFromByCategory = new HashMap<>();
        boolean hasDefault = false;

        for (FeePolicy policy : policies) {
            if (!ids.add(policy.id())) {
                throw new IllegalArgumentException("정책 id가 중복됩니다: " + policy.id());
            }
            if (policy.isDefault()) {
                hasDefault = true;
                if (policy.effectiveTo() != null) {
                    throw new IllegalArgumentException(
                            "기본 정책은 종료일을 가질 수 없습니다 (다음 버전의 시행일로 암묵 결정): " + policy.id());
                }
            }
            if (policy.effectiveTo() != null && policy.effectiveFrom().isAfter(policy.effectiveTo())) {
                throw new IllegalArgumentException(
                        "시행일이 종료일보다 늦습니다: " + policy.id()
                                + " (" + policy.effectiveFrom() + " > " + policy.effectiveTo() + ")");
            }
            String categoryKey = policy.categoryId() == null ? "" : policy.categoryId();
            Set<LocalDate> froms = effectiveFromByCategory.computeIfAbsent(categoryKey, k -> new HashSet<>());
            if (!froms.add(policy.effectiveFrom())) {
                throw new IllegalArgumentException(
                        "같은 카테고리에 같은 시행일의 정책이 중복됩니다: "
                                + (policy.isDefault() ? "(기본)" : policy.categoryId())
                                + " / " + policy.effectiveFrom());
            }
        }

        if (!hasDefault) {
            throw new IllegalArgumentException("기본 정책(categoryId == null)이 최소 1개 있어야 합니다");
        }
    }
}
