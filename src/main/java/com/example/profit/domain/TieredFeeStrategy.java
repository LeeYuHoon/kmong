package com.example.profit.domain;

import java.util.List;
import java.util.stream.Gatherers;

/**
 * 누진(marginal) 구간제 수수료. 구간별로 금액을 잘라 각각 계산한 뒤 합산한다.
 * 구간 구성은 데이터({@link Tier} 목록)이며, 구간 개수·경계가 바뀌어도 이 클래스는 수정되지 않는다.
 */
public record TieredFeeStrategy(List<Tier> tiers) implements FeeStrategy {

    public TieredFeeStrategy {
        tiers = List.copyOf(validate(tiers));
    }

    public static TieredFeeStrategy of(Tier... tiers) {
        return new TieredFeeStrategy(List.of(tiers));
    }

    private static List<Tier> validate(List<Tier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("구간은 1개 이상이어야 합니다");
        }
        if (tiers.getFirst().from().won() != 1) {
            throw new IllegalArgumentException(
                    "첫 구간은 1원부터 시작해야 합니다: " + tiers.getFirst().from());
        }
        tiers.subList(0, tiers.size() - 1).stream()
                .filter(Tier::isOpenEnded)
                .findFirst()
                .ifPresent(tier -> {
                    throw new IllegalArgumentException(
                            "마지막이 아닌 구간은 상한이 있어야 합니다: " + tier.label());
                });
        if (!tiers.getLast().isOpenEnded()) {
            throw new IllegalArgumentException(
                    "마지막 구간은 상한이 없어야 합니다: " + tiers.getLast().label());
        }
        // 인접한 두 구간씩 묶어 경계가 1원 단위로 이어지는지 확인한다 (Stream Gatherers, JDK 24+)
        tiers.stream()
                .gather(Gatherers.windowSliding(2))
                .filter(pair -> pair.size() == 2)
                .forEach(pair -> requireContiguous(pair.getFirst(), pair.getLast()));
        return tiers;
    }

    private static void requireContiguous(Tier current, Tier next) {
        long expectedNextFrom = current.to().won() + 1;
        if (next.from().won() != expectedNextFrom) {
            String reason = next.from().won() > expectedNextFrom ? "빈틈" : "겹침";
            throw new IllegalArgumentException(
                    "구간 사이에 " + reason + "이 있습니다: " + current.label() + " / " + next.label());
        }
    }

    @Override
    public ProfitBreakdown calculate(Money amount) {
        FeeStrategy.requirePositive(amount);
        List<TierResult> lines = tiers.stream()
                .map(tier -> {
                    Money portion = tier.amountWithin(amount);
                    return new TierResult(tier.label(), portion, tier.rate(), tier.rate().profitOf(portion));
                })
                .toList();
        return ProfitBreakdown.of(lines);
    }
}
