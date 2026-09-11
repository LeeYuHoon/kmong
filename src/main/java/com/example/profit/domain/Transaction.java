package com.example.profit.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 거래(주문 1건 = 결제 1회). 요율 확정 기준일은 결제일 {@code paidAt}이다.
 *
 * @param categoryId null이면 카테고리 예외 없이 기본 정책 적용
 */
public record Transaction(Money amount, LocalDate paidAt, String categoryId) {

    public Transaction {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(paidAt, "paidAt");
    }

    public static Transaction of(long amount, LocalDate paidAt, String categoryId) {
        return new Transaction(Money.of(amount), paidAt, categoryId);
    }
}
