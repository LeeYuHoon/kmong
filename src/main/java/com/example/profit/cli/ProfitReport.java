package com.example.profit.cli;

import com.example.profit.domain.FeePolicy;
import com.example.profit.domain.FlatFeeStrategy;
import com.example.profit.domain.Money;
import com.example.profit.domain.ProfitBreakdown;
import com.example.profit.domain.TierResult;
import com.example.profit.domain.TieredFeeStrategy;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 계산 결과와 요율표를 터미널용 텍스트로 만든다.
 * 한글은 터미널에서 두 칸을 차지하므로 표시 폭 기준으로 열을 맞춘다.
 */
final class ProfitReport {

    private static final int LABEL_WIDTH = 26;
    private static final int MONEY_WIDTH = 13;
    private static final int RATE_WIDTH = 6;

    private ProfitReport() {
    }

    static String breakdown(Money amount, LocalDate today, String categoryId, ProfitBreakdown result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[오늘 ").append(today)
                .append(" · 카테고리 ").append(categoryId == null ? "없음" : categoryId)
                .append(" · 적용 정책 ").append(result.appliedPolicyId()).append("]\n");
        sb.append(padRight("구간", LABEL_WIDTH))
                .append(padLeft("구간 금액", MONEY_WIDTH))
                .append(padLeft("요율", RATE_WIDTH))
                .append(padLeft("수익", MONEY_WIDTH)).append('\n');
        for (TierResult line : result.lines()) {
            sb.append(padRight(line.label(), LABEL_WIDTH))
                    .append(padLeft(line.amount().format(), MONEY_WIDTH))
                    .append(padLeft(line.rate().toString(), RATE_WIDTH))
                    .append(padLeft(line.profit().format(), MONEY_WIDTH)).append('\n');
        }
        sb.append("-".repeat(LABEL_WIDTH + MONEY_WIDTH * 2 + RATE_WIDTH)).append('\n');
        sb.append("견적금액 ").append(amount.format())
                .append(" · 수수료 ").append(amount.minus(result.total()).format())
                .append(" · 총수익 ").append(result.total().format()).append('\n');
        return sb.toString();
    }

    static String policies(List<FeePolicy> policies) {
        return policies.stream()
                .map(ProfitReport::policy)
                .collect(Collectors.joining("\n"));
    }

    private static String policy(FeePolicy p) {
        String scope = p.isDefault() ? "기본" : "카테고리 " + p.categoryId();
        String period = p.effectiveFrom() + " ~ " + (p.effectiveTo() == null ? "" : p.effectiveTo());
        String rates = switch (p.strategy()) {
            case TieredFeeStrategy tiered -> tiered.tiers().stream()
                    .map(t -> t.label() + " " + t.rate())
                    .collect(Collectors.joining(" / "));
            case FlatFeeStrategy flat -> "고정 " + flat.rate();
        };
        return "  " + padRight(p.id(), 14) + padRight(scope, 20) + padRight(period, 26) + rates;
    }

    static String padRight(String text, int width) {
        return text + " ".repeat(Math.max(0, width - displayWidth(text)));
    }

    static String padLeft(String text, int width) {
        return " ".repeat(Math.max(0, width - displayWidth(text))) + text;
    }

    /** 한글 음절·자모는 2칸, 나머지는 1칸으로 센다. */
    static int displayWidth(String text) {
        return text.codePoints().map(cp -> isWide(cp) ? 2 : 1).sum();
    }

    private static boolean isWide(int codePoint) {
        return (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0x1100 && codePoint <= 0x11FF)
                || (codePoint >= 0x3130 && codePoint <= 0x318F);
    }
}
