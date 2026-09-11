package com.example.profit.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 기간이 붙은 수수료 정책 한 버전. 불변 이력이며 수정하지 않고 새 버전을 추가한다.
 *
 * @param id            정책 식별자 (추적용)
 * @param categoryId    적용 카테고리. null이면 기본 정책
 * @param effectiveFrom 시행일(포함). 이 날짜 00시 이후 결제 건부터 적용
 * @param effectiveTo   종료일(포함). null이면 무기한. 카테고리 정책에만 허용
 * @param strategy      수수료 계산 전략
 */
public record FeePolicy(String id,
                        String categoryId,
                        LocalDate effectiveFrom,
                        LocalDate effectiveTo,
                        FeeStrategy strategy) {

    public FeePolicy {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(strategy, "strategy");
        if (id.isBlank()) {
            throw new IllegalArgumentException("정책 id는 비어 있을 수 없습니다");
        }
    }

    public static FeePolicy defaultPolicy(String id, LocalDate effectiveFrom, FeeStrategy strategy) {
        return new FeePolicy(id, null, effectiveFrom, null, strategy);
    }

    public static FeePolicy categoryPolicy(String id, String categoryId, LocalDate effectiveFrom,
                                           LocalDate effectiveTo, FeeStrategy strategy) {
        return new FeePolicy(id, Objects.requireNonNull(categoryId, "categoryId"),
                effectiveFrom, effectiveTo, strategy);
    }

    public boolean isDefault() {
        return categoryId == null;
    }

    public boolean isStartedBy(LocalDate date) {
        return !effectiveFrom.isAfter(date);
    }

    /** effectiveFrom <= date && (effectiveTo == null || date <= effectiveTo) */
    public boolean covers(LocalDate date) {
        return isStartedBy(date) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }
}
