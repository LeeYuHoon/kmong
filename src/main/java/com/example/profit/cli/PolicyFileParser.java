package com.example.profit.cli;

import com.example.profit.domain.FeePolicy;
import com.example.profit.domain.FeeStrategy;
import com.example.profit.domain.FlatFeeStrategy;
import com.example.profit.domain.Rate;
import com.example.profit.domain.Tier;
import com.example.profit.domain.TieredFeeStrategy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 수동 테스트용 요율표 텍스트 파일을 {@link FeePolicy} 목록으로 읽는다. 한 줄에 정책 하나.
 * <pre>
 * # id        | 카테고리 | 시행일     | 종료일 | 요율
 * default-v1  | -        | 2026-01-01 | -      | 1~500_000:20, 500_001~1_000_000:10, 1_000_001~:5
 * FIXED_10-v1 | FIXED_10 | 2026-01-01 | -      | flat:10
 * </pre>
 * <ul>
 *   <li>{@code #} 뒤는 주석, 빈 줄은 무시한다.</li>
 *   <li>{@code -}는 값 없음: 카테고리가 없으면 기본 정책, 종료일이 없으면 무기한.</li>
 *   <li>구간은 {@code from~to:요율}을 쉼표로 나열하며 마지막 구간은 {@code to}를 비운다. 금액에 {@code _}를 쓸 수 있다.</li>
 *   <li>고정 요율은 {@code flat:요율}. 요율은 소수({@code 18.5})도 허용한다.</li>
 * </ul>
 * 줄 단위 형식 오류는 줄 번호를 붙여 {@link IllegalArgumentException}으로 던진다.
 * 정책 간 정합성(기본 정책 존재, 시행일 중복 등)은 저장소 생성 시 {@code FeePolicyValidator}가 검증한다.
 */
public final class PolicyFileParser {

    private static final String NONE = "-";
    private static final String FLAT_PREFIX = "flat:";
    private static final int FIELD_COUNT = 5;

    private PolicyFileParser() {
    }

    public static List<FeePolicy> read(Path file) {
        try {
            return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("요율표 파일을 읽을 수 없습니다: " + file, e);
        }
    }

    public static List<FeePolicy> parse(List<String> lines) {
        List<FeePolicy> policies = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String content = stripComment(lines.get(i));
            if (content.isBlank()) {
                continue;
            }
            try {
                policies.add(parseLine(content));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new IllegalArgumentException((i + 1) + "번째 줄: " + e.getMessage(), e);
            }
        }
        return policies;
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    private static FeePolicy parseLine(String line) {
        String[] fields = Arrays.stream(line.split("\\|", -1)).map(String::strip).toArray(String[]::new);
        if (fields.length != FIELD_COUNT) {
            throw new IllegalArgumentException(
                    "칸이 " + FIELD_COUNT + "개(id | 카테고리 | 시행일 | 종료일 | 요율)여야 합니다: " + fields.length + "개");
        }
        String categoryId = orNull(fields[1]);
        LocalDate effectiveFrom = LocalDate.parse(fields[2]);
        String effectiveTo = orNull(fields[3]);
        return new FeePolicy(fields[0], categoryId, effectiveFrom,
                effectiveTo == null ? null : LocalDate.parse(effectiveTo), parseStrategy(fields[4]));
    }

    private static String orNull(String field) {
        return field.isEmpty() || field.equals(NONE) ? null : field;
    }

    private static FeeStrategy parseStrategy(String field) {
        if (field.startsWith(FLAT_PREFIX)) {
            return FlatFeeStrategy.of(parseRate(field.substring(FLAT_PREFIX.length())));
        }
        return new TieredFeeStrategy(Arrays.stream(field.split(","))
                .map(String::strip)
                .map(PolicyFileParser::parseTier)
                .toList());
    }

    /** {@code 1~500_000:20} 또는 마지막 구간 {@code 1_000_001~:5} */
    private static Tier parseTier(String token) {
        int tilde = token.indexOf('~');
        int colon = token.lastIndexOf(':');
        if (tilde < 0 || colon < tilde) {
            throw new IllegalArgumentException("구간은 from~to:요율 형식이어야 합니다: '" + token + "'");
        }
        long from = parseWon(token.substring(0, tilde));
        String to = token.substring(tilde + 1, colon).strip();
        Rate rate = parseRate(token.substring(colon + 1));
        return to.isEmpty() ? Tier.openEnded(from, rate) : Tier.of(from, parseWon(to), rate);
    }

    private static long parseWon(String text) {
        String digits = text.strip().replace("_", "");
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("금액이 숫자가 아닙니다: '" + text.strip() + "'", e);
        }
    }

    private static Rate parseRate(String text) {
        try {
            return Rate.of(text.strip().replace("%", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("요율이 숫자가 아닙니다: '" + text.strip() + "'", e);
        }
    }
}
