package com.example.profit.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProfitCliTest {

    private static final String POLICIES = """
            default-v1  | -        | 2026-01-01 | - | 1~500_000:20, 500_001~1_000_000:10, 1_000_001~:5
            default-v2  | -        | 2026-10-01 | - | 1~500_000:18, 500_001~1_000_000:10, 1_000_001~:5
            FIXED_10-v1 | FIXED_10 | 2026-01-01 | - | flat:10
            """;

    /** 시스템 시계 = 서울 2026-09-16 */
    private static final Clock SYSTEM_CLOCK = Clock.fixed(Instant.parse("2026-09-16T00:00:00Z"), ProfitCli.ZONE);

    @TempDir
    Path dir;
    private Path policyFile;

    @BeforeEach
    void writePolicies() throws IOException {
        policyFile = Files.writeString(dir.resolve("policies.txt"), POLICIES);
    }

    private record Run(int exitCode, String output) {
    }

    private static Run run(String input, String... args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        int exitCode = new ProfitCli(new BufferedReader(new StringReader(input)), out, SYSTEM_CLOCK).run(args);
        return new Run(exitCode, buffer.toString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("--amount가 있으면 한 번만 계산한다: 2026-10-05, 950,000원 → v2, 815,000원")
    void oneShot() {
        Run result = run("", "--policies", policyFile.toString(), "--today=2026-10-05", "--amount", "950000");

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
                .contains("적용 정책 default-v2")
                .contains("410,000원")
                .contains("총수익 815,000원");
    }

    @Test
    @DisplayName("--today를 생략하면 시스템 시계의 오늘(2026-09-16)을 쓴다 → v1")
    void oneShotDefaultsToSystemToday() {
        Run result = run("", "--policies", policyFile.toString(), "--amount", "950,000원");

        assertThat(result.output()).contains("[오늘 2026-09-16").contains("총수익 805,000원");
    }

    @Test
    @DisplayName("대화형: 날짜·카테고리를 바꿔 가며 계산하고, 잘못된 입력 뒤에도 계속 진행한다")
    void interactiveSession() {
        String input = String.join("\n",
                "2026-09-15",   // 오늘
                "",             // 카테고리 없음
                "950,000",      // → v1 805,000
                "abc",          // 오류
                "d", "2026-10-05",
                "950000",       // → v2 815,000
                "c", "FIXED_10",
                "950000",       // → 고정 10% 855,000
                "q");

        Run result = run(input, "--policies", policyFile.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.output())
                .contains("정책 3개를 읽었습니다")
                .containsSubsequence("총수익 805,000원", "견적금액은 숫자여야 합니다: abc",
                        "총수익 815,000원", "적용 정책 FIXED_10-v1", "총수익 855,000원");
    }

    @Test
    @DisplayName("대화형: 요율표 파일을 고치고 r을 입력하면 반영되고, 잘못 고치면 기존 요율표를 유지한다")
    void reloadPolicies() {
        String rateChanged = POLICIES.replace("1~500_000:20", "1~500_000:25");
        String broken = "v1 | - | 2026-01-01 | - | 1~500_000:20, 600_000~:10\n";
        // 줄 단위로 입력을 흘려보내며, 해당 줄을 읽기 직전에 파일을 고친다
        Reader input = new LineByLineReader(List.of("2026-09-16", "", "950000", "r", "950000", "r", "950000", "q"),
                Map.of(3, () -> Files.writeString(policyFile, rateChanged),
                        5, () -> Files.writeString(policyFile, broken)));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int exitCode = new ProfitCli(new BufferedReader(input),
                new PrintStream(buffer, true, StandardCharsets.UTF_8), SYSTEM_CLOCK)
                .run(new String[] {"--policies", policyFile.toString()});

        assertThat(exitCode).isZero();
        assertThat(buffer.toString(StandardCharsets.UTF_8)).containsSubsequence(
                "총수익 805,000원",
                "1원 ~ 500,000원 25%",
                "총수익 780,000원",
                "오류: 1번째 줄:", "기존 요율표를 그대로 사용합니다",
                "총수익 780,000원");
    }

    /** read() 한 번에 한 줄씩만 돌려주는 Reader. 지정한 줄 번호를 돌려주기 직전에 동작을 실행한다. */
    private static final class LineByLineReader extends Reader {

        interface Action {
            void run() throws IOException;
        }

        private final List<String> lines;
        private final Map<Integer, Action> beforeLine;
        private int next;

        LineByLineReader(List<String> lines, Map<Integer, Action> beforeLine) {
            this.lines = lines;
            this.beforeLine = beforeLine;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (next >= lines.size()) {
                return -1;
            }
            Action action = beforeLine.get(next);
            if (action != null) {
                action.run();
            }
            char[] line = (lines.get(next++) + "\n").toCharArray();
            System.arraycopy(line, 0, cbuf, off, line.length);
            return line.length;
        }

        @Override
        public void close() {
        }
    }

    @Test
    @DisplayName("대화형: 적용 가능한 정책이 없는 날짜는 오류만 출력하고 계속 진행한다")
    void noApplicablePolicy() {
        Run result = run("2025-06-01\n\n950000\nq\n", "--policies", policyFile.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("오류: 적용 가능한 수수료 정책이 없습니다");
    }

    @Test
    @DisplayName("입력이 끝나면(EOF) 정상 종료한다")
    void endOfInput() {
        assertThat(run("2026-09-16\n", "--policies", policyFile.toString()).exitCode()).isZero();
    }

    @Test
    @DisplayName("요율표가 잘못되면 한 번 계산 모드는 줄 번호가 담긴 오류와 함께 1을 반환한다")
    void invalidPolicyFile() throws IOException {
        Files.writeString(policyFile, "v1 | - | 2026-01-01 | - | 1~500_000:20, 600_000~:10\n");

        Run result = run("", "--policies", policyFile.toString(), "--amount", "950000");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output()).contains("오류: 1번째 줄:").contains("빈틈");
    }

    @Test
    @DisplayName("알 수 없는 옵션은 사용법과 함께 2를 반환한다")
    void unknownOption() {
        Run result = run("", "--price", "950000");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.output()).contains("알 수 없는 옵션: --price").contains("사용법");
    }
}
