package com.example.profit.policy;

import com.example.profit.domain.FeePolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * 메모리 정책 저장소. 생성 및 추가 시마다 전체 검증을 수행하며,
 * 기존 정책은 수정·삭제하지 않고 새 버전을 추가하는 방식만 제공한다.
 */
public final class InMemoryFeePolicyRepository implements FeePolicyRepository {

    private volatile List<FeePolicy> policies;

    public InMemoryFeePolicyRepository(List<FeePolicy> initial) {
        FeePolicyValidator.validate(initial);
        this.policies = List.copyOf(initial);
    }

    public static InMemoryFeePolicyRepository of(FeePolicy... policies) {
        return new InMemoryFeePolicyRepository(List.of(policies));
    }

    /** 새 정책 버전을 추가한다. 추가 후 전체 정합성이 깨지면 예외를 던지고 저장소는 변경되지 않는다. */
    public synchronized void add(FeePolicy policy) {
        List<FeePolicy> next = new ArrayList<>(policies);
        next.add(policy);
        FeePolicyValidator.validate(next);
        this.policies = List.copyOf(next);
    }

    @Override
    public List<FeePolicy> findAll() {
        return policies;
    }
}
