package com.example.profit.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 누진(marginal) 구간제 수수료. 구간별로 금액을 잘라 각각 계산한 뒤 합산한다.
 * 구간 구성은 데이터({@link Tier} 목록)이며, 구간 개수·경계가 바뀌어도 이 클래스는 수정되지 않는다.
 */
public final class TieredFeeStrategy implements FeeStrategy {

    private final List<Tier> tiers;

    public TieredFeeStrategy(List<Tier> tiers) {
        this.tiers = List.copyOf(validate(tiers));
    }

    public static TieredFeeStrategy of(Tier... tiers) {
        return new TieredFeeStrategy(List.of(tiers));
    }

    public List<Tier> tiers() {
        return tiers;
    }

    private static List<Tier> validate(List<Tier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("구간은 1개 이상이어야 합니다");
        }
        if (tiers.getFirst().from().won() != 1) {
            throw new IllegalArgumentException(
                    "첫 구간은 1원부터 시작해야 합니다: " + tiers.getFirst().from());
        }
        for (int i = 0; i < tiers.size(); i++) {
            Tier current = tiers.get(i);
            boolean last = i == tiers.size() - 1;
            if (last) {
                if (!current.isOpenEnded()) {
                    throw new IllegalArgumentException(
                            "마지막 구간은 상한이 없어야 합니다: " + current.label());
                }
                continue;
            }
            if (current.isOpenEnded()) {
                throw new IllegalArgumentException(
                        "마지막이 아닌 구간은 상한이 있어야 합니다: " + current.label());
            }
            Tier next = tiers.get(i + 1);
            long expectedNextFrom = current.to().won() + 1;
            if (next.from().won() != expectedNextFrom) {
                String reason = next.from().won() > expectedNextFrom ? "빈틈" : "겹침";
                throw new IllegalArgumentException(
                        "구간 사이에 " + reason + "이 있습니다: " + current.label() + " / " + next.label());
            }
        }
        return tiers;
    }

    @Override
    public ProfitBreakdown calculate(Money amount) {
        FeeStrategy.requirePositive(amount);
        List<TierResult> lines = new ArrayList<>(tiers.size());
        for (Tier tier : tiers) {
            Money portion = tier.amountWithin(amount);
            Money profit = tier.rate().profitOf(portion);
            lines.add(new TierResult(tier.label(), portion, tier.rate(), profit));
        }
        return ProfitBreakdown.of(lines);
    }
}
