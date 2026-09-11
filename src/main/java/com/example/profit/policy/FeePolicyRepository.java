package com.example.profit.policy;

import com.example.profit.domain.FeePolicy;

import java.util.List;

public interface FeePolicyRepository {

    /** 저장된 모든 정책 버전(불변 이력). */
    List<FeePolicy> findAll();
}
