package com.example.profit.cli;

import com.example.profit.domain.FeePolicy;
import com.example.profit.domain.FlatFeeStrategy;
import com.example.profit.domain.Rate;
import com.example.profit.domain.Tier;
import com.example.profit.domain.TieredFeeStrategy;
import com.example.profit.policy.InMemoryFeePolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyFileParserTest {

    @Test
    @DisplayName("구간제·고정율 정책을 읽고, 주석·빈 줄은 무시한다")
    void parsesTieredAndFlat() {
        List<FeePolicy> policies = PolicyFileParser.parse(List.of(
                "# id | 카테고리 | 시행일 | 종료일 | 요율",
                "",
                "default-v1  | -        | 2026-01-01 | -          | 1~500_000:20, 500_001~1_000_000:10, 1_000_001~:5",
                "FIXED_10-v1 | FIXED_10 | 2026-01-01 | 2026-06-30 | flat:10   # 기간 한정"));

        assertThat(policies).containsExactly(
                FeePolicy.defaultPolicy("default-v1", LocalDate.of(2026, 1, 1), TieredFeeStrategy.of(
                        Tier.of(1, 500_000, Rate.of(20)),
                        Tier.of(500_001, 1_000_000, Rate.of(10)),
                        Tier.openEnded(1_000_001, Rate.of(5)))),
                FeePolicy.categoryPolicy("FIXED_10-v1", "FIXED_10",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), FlatFeeStrategy.of(Rate.of(10))));
    }

    @Test
    @DisplayName("소수 요율과 % 표기를 허용한다")
    void decimalRate() {
        FeePolicy policy = PolicyFileParser.parse(List.of(
                "v | - | 2026-01-01 | - | 1~100:18.5%, 101~:5")).getFirst();

        assertThat(((TieredFeeStrategy) policy.strategy()).tiers().getFirst().rate()).isEqualTo(Rate.of("18.5"));
    }

    @Test
    @DisplayName("칸 수가 틀리면 줄 번호와 함께 실패")
    void wrongFieldCount() {
        assertThatThrownBy(() -> PolicyFileParser.parse(List.of(
                "# 주석",
                "v1 | - | 2026-01-01 | flat:10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("2번째 줄:")
                .hasMessageContaining("칸이 5개");
    }

    @Test
    @DisplayName("구간 사이에 빈틈이 있으면 도메인 검증 메시지를 줄 번호와 함께 전달")
    void tierGap() {
        assertThatThrownBy(() -> PolicyFileParser.parse(List.of(
                "v1 | - | 2026-01-01 | - | 1~500_000:20, 600_000~:10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("1번째 줄:")
                .hasMessageContaining("빈틈");
    }

    @Test
    @DisplayName("날짜·금액·요율 형식 오류")
    void malformedValues() {
        assertThatThrownBy(() -> PolicyFileParser.parse(List.of("v1 | - | 2026-13-01 | - | flat:10")))
                .hasMessageStartingWith("1번째 줄:");
        assertThatThrownBy(() -> PolicyFileParser.parse(List.of("v1 | - | 2026-01-01 | - | 1~오십만:20, 500_001~:10")))
                .hasMessageContaining("금액이 숫자가 아닙니다");
        assertThatThrownBy(() -> PolicyFileParser.parse(List.of("v1 | - | 2026-01-01 | - | flat:열")))
                .hasMessageContaining("요율이 숫자가 아닙니다");
        assertThatThrownBy(() -> PolicyFileParser.parse(List.of("v1 | - | 2026-01-01 | - | 1-500_000:20")))
                .hasMessageContaining("from~to:요율");
    }

    @Test
    @DisplayName("프로젝트 루트의 샘플 policies.txt는 파싱과 저장소 검증을 통과한다")
    void sampleFileIsValid() {
        List<FeePolicy> policies = PolicyFileParser.read(Path.of(ProfitCli.DEFAULT_POLICY_FILE));

        assertThat(policies).extracting(FeePolicy::id).containsExactly("default-v1", "default-v2", "FIXED_10-v1");
        new InMemoryFeePolicyRepository(policies);
    }
}
