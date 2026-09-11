package com.example.profit.support;

import com.example.profit.domain.FeeStrategy;
import com.example.profit.domain.Rate;
import com.example.profit.domain.Tier;
import com.example.profit.domain.TieredFeeStrategy;

/** 테스트 공용 요율표 픽스처. */
public final class Policies {

    private Policies() {
    }

    /** 기준 요율표: 1~500,000 20% / 500,001~1,000,000 10% / 1,000,001~ 5% */
    public static TieredFeeStrategy standardTiers() {
        return TieredFeeStrategy.of(
                Tier.of(1, 500_000, Rate.of(20)),
                Tier.of(500_001, 1_000_000, Rate.of(10)),
                Tier.openEnded(1_000_001, Rate.of(5)));
    }

    /** 1구간만 18%로 바뀐 요율표 (v2). */
    public static FeeStrategy tiersWithFirstTier18() {
        return TieredFeeStrategy.of(
                Tier.of(1, 500_000, Rate.of(18)),
                Tier.of(500_001, 1_000_000, Rate.of(10)),
                Tier.openEnded(1_000_001, Rate.of(5)));
    }
}
