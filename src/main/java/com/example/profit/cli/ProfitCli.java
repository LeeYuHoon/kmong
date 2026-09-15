package com.example.profit.cli;

import com.example.profit.application.ProfitCalculator;
import com.example.profit.domain.FeePolicy;
import com.example.profit.domain.Money;
import com.example.profit.domain.ProfitBreakdown;
import com.example.profit.policy.FeePolicyResolver;
import com.example.profit.policy.InMemoryFeePolicyRepository;
import com.example.profit.policy.NoApplicablePolicyException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 사람이 직접 계산 결과를 확인하기 위한 CLI.
 * 요율표는 파일({@link PolicyFileParser})로, 오늘 날짜·카테고리·견적금액은 입력으로 받는다.
 * 입력한 오늘 날짜는 {@link Clock#fixed}로 {@link ProfitCalculator}에 주입된다.
 * <pre>
 * ./gradlew -q --console=plain run                                   # 대화형
 * ./gradlew -q --console=plain run --args="--today 2026-10-05 --amount 950000"   # 한 번만 계산
 * </pre>
 */
public final class ProfitCli {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final String DEFAULT_POLICY_FILE = "policies.txt";

    private static final Set<String> OPTIONS = Set.of("policies", "today", "category", "amount");
    private static final String MENU = "견적금액 (d 날짜 · c 카테고리 · r 요율표 다시 읽기 · p 요율표 · q 종료) > ";

    private final BufferedReader in;
    private final PrintStream out;
    private final Clock systemClock;

    private Path policyFile;
    private List<FeePolicy> policies;
    private FeePolicyResolver resolver;
    private LocalDate today;
    private String categoryId;

    ProfitCli(BufferedReader in, PrintStream out, Clock systemClock) {
        this.in = in;
        this.out = out;
        this.systemClock = systemClock;
    }

    public static void main(String[] args) {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        int exitCode = new ProfitCli(stdin, System.out, Clock.system(ZONE)).run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    int run(String[] args) {
        Map<String, String> options;
        try {
            options = parseOptions(args);
        } catch (IllegalArgumentException e) {
            out.println("오류: " + e.getMessage());
            out.println("사용법: [--policies 파일] [--today yyyy-MM-dd] [--category 카테고리] [--amount 금액]");
            return 2;
        }
        return options.containsKey("amount") ? runOnce(options) : runInteractive(options);
    }

    private int runOnce(Map<String, String> options) {
        try {
            loadPolicies(Path.of(options.getOrDefault("policies", DEFAULT_POLICY_FILE)));
            today = options.containsKey("today") ? LocalDate.parse(options.get("today")) : LocalDate.now(systemClock);
            categoryId = options.get("category");
            calculate(options.get("amount"));
            return 0;
        } catch (RuntimeException e) {
            out.println("오류: " + e.getMessage());
            return 1;
        }
    }

    private int runInteractive(Map<String, String> options) {
        out.println("=== 총수익 계산기 (수동 테스트) ===");
        if (!initPolicies(options.get("policies"))) {
            return 0;
        }
        if (options.containsKey("today")) {
            today = LocalDate.parse(options.get("today"));
        } else if (!askToday()) {
            return 0;
        }
        if (options.containsKey("category")) {
            categoryId = options.get("category");
        } else if (!askCategory()) {
            return 0;
        }

        while (true) {
            String input = ask(MENU);
            if (input == null || input.equals("q")) {
                return 0;
            }
            switch (input) {
                case "" -> { }
                case "d" -> {
                    if (!askToday()) {
                        return 0;
                    }
                }
                case "c" -> {
                    if (!askCategory()) {
                        return 0;
                    }
                }
                case "r" -> reloadPolicies();
                case "p" -> out.println(ProfitReport.policies(policies));
                default -> calculateSafely(input);
            }
        }
    }

    /** 옵션으로 받은 파일이 없으면 경로를 묻고, 읽기에 실패하면 다시 묻는다. EOF면 false. */
    private boolean initPolicies(String fromOption) {
        String path = fromOption;
        while (true) {
            if (path == null) {
                path = ask("요율표 파일 [" + DEFAULT_POLICY_FILE + "]: ");
                if (path == null) {
                    return false;
                }
                if (path.isEmpty()) {
                    path = DEFAULT_POLICY_FILE;
                }
            }
            try {
                loadPolicies(Path.of(path));
                printPolicies();
                return true;
            } catch (RuntimeException e) {
                out.println("오류: " + e.getMessage());
                path = null;
            }
        }
    }

    private void reloadPolicies() {
        try {
            loadPolicies(policyFile);
            printPolicies();
        } catch (RuntimeException e) {
            out.println("오류: " + e.getMessage());
            out.println("기존 요율표를 그대로 사용합니다.");
        }
    }

    /** 파싱과 저장소 정합성 검증이 모두 통과해야 교체한다. */
    private void loadPolicies(Path file) {
        List<FeePolicy> loaded = PolicyFileParser.read(file);
        FeePolicyResolver loadedResolver = new FeePolicyResolver(new InMemoryFeePolicyRepository(loaded));
        policyFile = file;
        policies = loaded;
        resolver = loadedResolver;
    }

    private void printPolicies() {
        out.println(policyFile + " 에서 정책 " + policies.size() + "개를 읽었습니다.");
        out.println(ProfitReport.policies(policies));
    }

    private boolean askToday() {
        LocalDate systemToday = LocalDate.now(systemClock);
        while (true) {
            String input = ask("오늘 날짜 yyyy-MM-dd [" + systemToday + "]: ");
            if (input == null) {
                return false;
            }
            try {
                today = input.isEmpty() ? systemToday : LocalDate.parse(input);
                return true;
            } catch (DateTimeParseException e) {
                out.println("오류: 날짜는 yyyy-MM-dd 형식이어야 합니다: " + input);
            }
        }
    }

    private boolean askCategory() {
        String input = ask("카테고리 (없으면 Enter): ");
        if (input == null) {
            return false;
        }
        categoryId = input.isEmpty() ? null : input;
        return true;
    }

    private void calculateSafely(String amountText) {
        try {
            calculate(amountText);
        } catch (IllegalArgumentException | NoApplicablePolicyException e) {
            out.println("오류: " + e.getMessage());
        }
    }

    private void calculate(String amountText) {
        Money amount = Money.of(parseAmount(amountText));
        Clock fixedToday = Clock.fixed(today.atStartOfDay(ZONE).toInstant(), ZONE);
        ProfitBreakdown result = new ProfitCalculator(fixedToday, resolver).calculateProfitAmount(amount, categoryId);
        out.println();
        out.print(ProfitReport.breakdown(amount, today, categoryId, result));
        out.println();
    }

    /** 950000, 950,000, 950_000, 950,000원 모두 허용한다. */
    private static long parseAmount(String text) {
        String digits = text.replaceAll("[,_\\s원]", "");
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("견적금액은 숫자여야 합니다: " + text);
        }
    }

    private String ask(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            String line = in.readLine();
            return line == null ? null : line.strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("알 수 없는 인자: " + arg);
            }
            String key;
            String value;
            int eq = arg.indexOf('=');
            if (eq >= 0) {
                key = arg.substring(2, eq);
                value = arg.substring(eq + 1);
            } else {
                key = arg.substring(2);
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--" + key + " 값이 없습니다");
                }
                value = args[++i];
            }
            if (!OPTIONS.contains(key)) {
                throw new IllegalArgumentException("알 수 없는 옵션: --" + key);
            }
            options.put(key, value);
        }
        return options;
    }
}
