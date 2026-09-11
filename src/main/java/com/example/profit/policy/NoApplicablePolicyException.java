package com.example.profit.policy;

import java.time.LocalDate;

public class NoApplicablePolicyException extends RuntimeException {

    public NoApplicablePolicyException(LocalDate date, String categoryId) {
        super("적용 가능한 수수료 정책이 없습니다: date=" + date
                + ", categoryId=" + (categoryId == null ? "(기본)" : categoryId));
    }
}
