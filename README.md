# 거래 완료 금액 기반 총수익 계산기

거래 완료 금액에서 플랫폼 수수료를 뺀 **총수익**을 계산하는 순수 Java 25 프로젝트입니다.
수수료는 누진(marginal) 구간제로, 금액을 구간별로 잘라 각각 계산한 뒤 합산합니다.

핵심 진입점은 `ProfitCalculator.calculateProfitAmount(...)`이며, 요율은 코드에 박힌 상수가 아니라
**기간이 붙은 정책 이력(`FeePolicy`)** 으로 관리합니다. 덕분에 요율 변경, 구간 추가, 카테고리 예외를
모두 "새 정책 버전 추가"로 처리할 수 있고, 이미 결제된 거래의 수익은 나중에 요율이 바뀌어도 변하지 않습니다.

## 1. 실행 방법

```bash
./gradlew test
```

- JDK 25 필요, Gradle 9.7.1 Wrapper 포함 (별도 Gradle 설치 불필요)
- `JAVA_HOME`이 JDK 25를 가리켜야 합니다. `java -version`이 아니라 `"$JAVA_HOME/bin/java" -version`으로 확인하세요.
- 의존성: JUnit 5, AssertJ (테스트 전용). 프레임워크 없음.
- 테스트 로그에 각 테스트의 통과/실패 이벤트가 출력되고, 마지막에 `Test summary: SUCCESS (N tests, ...)`가 찍힙니다.

## 2. 요율표와 계산 방식

### 기준 요율표

| 구간 | 수수료 |
|---|---|
| 1원 ~ 500,000원 | 20% |
| 500,001원 ~ 1,000,000원 | 10% |
| 1,000,001원 ~ | 5% |

### 누진(marginal) 계산

금액을 구간별로 잘라 각 구간에 그 구간의 요율만 적용하고 합산합니다.

```
구간 금액 = max(0, min(amount, to) - from + 1)     (마지막 구간은 to가 없으므로 amount - from + 1)
구간 수익 = 구간 금액 × (100 − 요율) / 100
총수익    = Σ 구간 수익
```

| 거래 완료 금액 | 1구간 수익 | 2구간 수익 | 3구간 수익 | 총수익 |
|---|---|---|---|---|
| 400,000 | 320,000 | 0 | 0 | 320,000 |
| 950,000 | 400,000 | 405,000 | 0 | 805,000 |
| 2,000,000 | 400,000 | 450,000 | 950,000 | 1,800,000 |
| 5,000,000 | 400,000 | 450,000 | 3,800,000 | 4,650,000 |

금액이 구간에 도달하지 못해도 내역(`ProfitBreakdown.lines`)에는 0원 행이 남습니다. 화면에 모든 구간을 표시하는 UI를 그대로 지원하기 위해서입니다.

### 절사 규칙

- 구간별 수익을 **원 단위로 절사(`RoundingMode.DOWN`)** 한 뒤 합산합니다.
  예: 333,333원 → 1구간 333,333 × 0.8 = 266,666.4 → **266,666원**
- 라운딩 모드는 `ProfitRounding.MODE` 상수 하나에 모여 있어 정책이 바뀌면 그 상수만 교체합니다.
- 금액은 `long`(원), 요율은 `BigDecimal`(퍼센트)입니다. 소수 요율(예: 18.5%)에도 오차 없이 대응하며 `double`은 사용하지 않습니다.

## 3. 설계 원칙

1. **요율 확정 기준일은 결제일(`paidAt`)이다.**
   요율이 나중에 바뀌어도 이미 결제된 거래의 총수익은 바뀌지 않습니다. 이는 크몽의 실제 정책과 같습니다.
   변경된 수수료율은 시행일 00시 이후 결제 건에만 적용되고, 그 이전 결제 건에는 소급되지 않습니다.
2. **정책은 불변 이력이다.**
   요율 변경, 구간 추가/수정, 카테고리 예외는 모두 새 `FeePolicy` 버전을 추가하는 방식으로 처리합니다.
   기존 정책 객체는 수정하지 않으며, 저장소(`InMemoryFeePolicyRepository`)는 `add`만 제공하고 수정·삭제는 제공하지 않습니다.
3. **구간은 코드가 아니라 데이터다.**
   구간 개수와 경계는 `List<Tier>`로 주어지며, `TieredFeeStrategy.calculate`는 구간 목록을 순회할 뿐입니다.
   구간이 3개에서 4개로 늘어나도 계산 로직은 한 줄도 바뀌지 않습니다. (6절 참고)
4. **구간제와 고정율은 같은 인터페이스를 구현한다.**
   `FeeStrategy`는 `sealed interface`이고 `TieredFeeStrategy`, `FlatFeeStrategy`가 구현합니다.
   `ProfitCalculator`는 정책에서 전략을 꺼내 `calculate(amount)`를 호출할 뿐, 전략의 종류를 알지 못합니다.
5. **현재 날짜는 `java.time.Clock`으로 주입한다.**
   소스 어디에서도 `LocalDate.now()` 등 인자 없는 `now()`를 호출하지 않습니다. 테스트는 `Clock.fixed(...)`로 오늘을 고정해 결정적으로 실행됩니다.
6. **반올림은 원 단위 절사, 구간별로 절사한 뒤 합산한다.** (2절 참고)
7. **금액은 `long`, 요율은 `BigDecimal`, `double` 금지.**
8. **프레임워크 없이 순수 Java.** 의존성은 JUnit 5와 AssertJ뿐입니다.

### Java 25 기능 활용

기능을 쓰기 위해 쓰지 않고, 코드가 더 명확해지는 곳에만 적용했습니다.

| 기능 | 적용 위치 | 효과 |
|---|---|---|
| Stream Gatherers (`Gatherers.windowSliding`) | `TieredFeeStrategy` 구간 검증 | 인덱스 루프 없이 인접한 두 구간씩 묶어 경계 연속성을 검사 |
| Flexible Constructor Bodies | `NoApplicablePolicyException` | `super()` 호출 전에 메시지 조립 |
| Unnamed Variables (`_`) | `FeePolicyValidator` | 쓰지 않는 람다 파라미터를 명시 |
| `record` + `sealed interface` | `TieredFeeStrategy`, `FlatFeeStrategy` | 전략 구현체를 불변 값 객체로 통일, 값 동등성 확보 |
| Sequenced Collections (`getFirst`/`getLast`) | 구간 검증, 테스트 | 첫/마지막 요소 접근 |

## 4. 시나리오 1 해설: "다음 달부터 1구간 수수료가 20% → 18%로 변경된다. 지난달 거래를 다시 계산해야 한다."

### 해석

"지난달 거래를 다시 계산한다"를 **재계산해도 결과가 바뀌면 안 된다**는 요구로 해석했습니다.

근거는 크몽의 수수료 정책입니다. 크몽은 수수료율 변경 시 **시행일 00시 이후 결제되는 건부터** 새 요율을 적용하고,
그 이전에 결제된 거래에는 소급 적용하지 않습니다. 지난달에 결제된 거래는 결제 시점에 이미 요율이 확정되었고,
그 거래의 총수익은 전문가에게 이미 안내(또는 정산)된 금액입니다. 다음 달 요율이 바뀌었다고 해서
지난달 정산 내역이 재계산 시점마다 달라진다면 정산 데이터의 신뢰성이 깨집니다.

즉 여기서 "다시 계산"은 정산 재검증, 리포트 재생성, 장애 후 재처리처럼 **같은 입력으로 같은 답을 다시 얻어야 하는 상황**이고,
요율을 상수로 두고 `20 → 18`로 바꾸는 순간 그 답이 바뀌어 버리는 것이 문제입니다.

### 대응

요율을 상수가 아니라 **기간이 붙은 정책 이력**으로 관리합니다.

```java
// v1: 2026-01-01 시행 (기존 요율표)
FeePolicy v1 = FeePolicy.defaultPolicy("default-v1", LocalDate.of(2026, 1, 1),
        TieredFeeStrategy.of(
                Tier.of(1, 500_000, Rate.of(20)),
                Tier.of(500_001, 1_000_000, Rate.of(10)),
                Tier.openEnded(1_000_001, Rate.of(5))));

// v2: 2026-10-01 시행 (1구간 18%) — v1은 수정하지 않고 새 버전을 추가한다
FeePolicy v2 = FeePolicy.defaultPolicy("default-v2", LocalDate.of(2026, 10, 1),
        TieredFeeStrategy.of(
                Tier.of(1, 500_000, Rate.of(18)),
                Tier.of(500_001, 1_000_000, Rate.of(10)),
                Tier.openEnded(1_000_001, Rate.of(5))));

repository.add(v2);
```

`ProfitCalculator.calculateProfitAmount(Transaction tx)`는 `tx.paidAt()` 기준으로 정책을 고릅니다.
`FeePolicyResolver`는 기본 정책 중 `effectiveFrom <= paidAt`인 것 가운데 가장 늦은 버전을 선택하므로,

- 2026-08-20 결제 950,000원 → v1 → **805,000원** (v2 추가 전후 동일)
- 2026-10-05 결제 950,000원 → v2 → 1구간 410,000원 + 2구간 405,000원 = **815,000원**

결과의 `appliedPolicyId`에 어떤 정책 버전이 적용되었는지 남으므로, 정산 내역마다 "어느 요율표로 계산되었는가"를 추적할 수 있습니다.
이 흐름은 `ProfitCalculatorTest`의 시나리오 1에서 그대로 검증합니다.

### 다른 해석 가능성과 대응

- **정산일 기준 소급**: "정산 시점의 요율을 적용한다"는 정책이라면 결제일 대신 정산일을 기준일로 넘기면 됩니다.
  `calculateProfitAmount(amount, categoryId, LocalDate asOf)`에 정산일을 넣으면 되고, 정책 데이터와 계산 로직은 그대로입니다.
  단, 이 경우 같은 거래의 수익이 정산 시점에 따라 달라지므로 정산 결과와 함께 `appliedPolicyId`를 반드시 저장해야 합니다.
- **what-if 시뮬레이션**: "지난달 거래에 새 요율을 적용하면 얼마였을까"를 보고 싶다면 같은 `asOf` 오버로드에
  미래 날짜(예: 2026-10-01)를 넣어 계산합니다. 실제 정산 데이터는 건드리지 않고 시뮬레이션만 가능합니다.
- **진짜 소급 변경**(정책 실수 정정 등): 정책 객체를 수정하는 대신 잘못된 버전과 같은 `effectiveFrom`을 갖는 정정 버전을 추가하는 것이
  자연스럽지만, 현재 `FeePolicyValidator`는 같은 카테고리 내 `effectiveFrom` 중복을 금지하므로 그대로는 불가능합니다.
  이런 요구가 생기면 정책에 `supersedes`/`version` 필드를 추가해 "같은 시행일의 최신 버전"을 고르도록 Resolver를 확장하는 것이 이 설계의 확장 지점입니다.

## 5. 시나리오 2 해설: "특정 카테고리는 구간과 무관하게 고정 10%가 적용된다."

고정 요율은 `FlatFeeStrategy`로, 카테고리 예외는 `categoryId`가 있는 `FeePolicy`로 표현합니다.

```java
FeePolicy fixed10 = FeePolicy.categoryPolicy(
        "FIXED_10-v1", "FIXED_10",
        LocalDate.of(2026, 1, 1), null,          // effectiveTo == null: 무기한
        FlatFeeStrategy.of(Rate.of(10)));
repository.add(fixed10);
```

`FeePolicyResolver.resolve(date, categoryId)`는

1. `categoryId`가 있으면 해당 카테고리 정책 중 `effectiveFrom <= date <= effectiveTo`인 것 가운데 가장 늦은 버전을 고르고,
2. 없으면 기본 정책으로 내려갑니다.

따라서 카테고리 `FIXED_10`의 950,000원은 855,000원(내역 1행), 다른 카테고리나 카테고리 없는 거래는 기본 구간제로 805,000원이 됩니다.
`ProfitCalculator`는 전략이 구간제인지 고정율인지 전혀 알지 못하고 `FeeStrategy.calculate`만 호출합니다.

카테고리 정책에는 **`effectiveTo`(종료일)** 를 둘 수 있습니다. 프로모션처럼 기간 한정으로 고정 10%를 적용하고
종료일이 지나면 자동으로 기본 정책에 복귀합니다. 기본 정책은 종료일을 갖지 않으며(항상 무언가는 적용되어야 하므로),
다음 버전의 시행일이 곧 이전 버전의 암묵적 종료일입니다. 이 차이는 `FeePolicyValidator`가 강제합니다.

## 6. 구간 변경 대응: 500,001 ~ 750,000원 15% 구간 추가

구간은 코드가 아니라 데이터이므로, 4구간 요율표는 새 정책 버전으로 추가할 뿐입니다.

```java
FeePolicy v3 = FeePolicy.defaultPolicy("default-v3", LocalDate.of(2027, 1, 1),
        TieredFeeStrategy.of(
                Tier.of(1, 500_000, Rate.of(20)),
                Tier.of(500_001, 750_000, Rate.of(15)),      // 새 구간
                Tier.of(750_001, 1_000_000, Rate.of(10)),
                Tier.openEnded(1_000_001, Rate.of(5))));
repository.add(v3);
```

`TieredFeeStrategy.calculate`는 구간 목록을 순회하며 각 구간의 `amountWithin(amount)`에 요율을 적용해 합산하므로
구간이 3개든 4개든 로직은 동일합니다. 950,000원은 400,000 + 212,500 + 180,000 + 0 = **792,500원**이 됩니다
(`TieredFeeStrategyTest.fourTierPolicy`).

잘못된 구간 구성은 `TieredFeeStrategy` 생성 시점에 `IllegalArgumentException`으로 막습니다.

- 구간 1개 이상
- 첫 구간 `from == 1`
- 이전 구간 `to + 1 == 다음 구간 from` (빈틈·겹침 금지)
- 마지막 구간만 `to == null`, 나머지는 `from <= to`

## 7. 가정

- **주문 1건 = 결제 1회.** 옵션 추가 결제, 부분 취소, 분할 결제는 범위 밖입니다. 결제 1회마다 결제일 기준으로 정책을 고르고 그 금액 전체에 누진 구간을 적용합니다.
- **절사 규칙.** 구간별 수익을 원 단위로 절사한 뒤 합산합니다. 총액 절사가 아니므로 예시 UI의 구간별 표시 금액과 합계가 항상 일치합니다.
- **부가세·결제망 이용료 없음.** 총수익 = Σ(구간 금액 × (100 − 요율) / 100)으로 끝납니다.
- **결제일은 날짜(`LocalDate`) 단위.** 시행일 00시 기준이므로 날짜만 있으면 충분합니다. 시각이 필요한 시스템에서는 결제 `Instant`를 서비스 시간대의 `LocalDate`로 변환해 넘기면 됩니다.
- **금액은 1원 이상.** 0원 이하는 `IllegalArgumentException`입니다.
- **카테고리 정책이 없는 카테고리**는 기본 정책을 따릅니다. 카테고리 정책은 기본 정책보다 항상 우선합니다.

## 8. 패키지 구조

```
com.example.profit
├── domain
│   ├── Money                 값 객체. long won. 음수 금지. plus/minus/min.
│   ├── Rate                  값 객체. BigDecimal percent, 0~100 검증. of(20), of("18.5"). profitOf(Money).
│   ├── ProfitRounding        라운딩 모드 상수 (RoundingMode.DOWN)
│   ├── Tier                  record(Money from, Money to /*null = 무한*/, Rate rate). amountWithin, label.
│   ├── FeeStrategy           sealed interface: ProfitBreakdown calculate(Money amount)
│   ├── TieredFeeStrategy     record(List<Tier> tiers). compact 생성자에서 구간 정합성 검증. 누진 계산.
│   ├── FlatFeeStrategy       record(Rate rate). 내역 단일 행.
│   ├── FeePolicy             record(id, categoryId /*null = 기본*/, effectiveFrom, effectiveTo /*카테고리만*/, strategy)
│   ├── ProfitBreakdown       record(Money total, List<TierResult> lines, String appliedPolicyId)
│   ├── TierResult            record(String label, Money amount, Rate rate, Money profit)
│   └── Transaction           record(Money amount, LocalDate paidAt, String categoryId /*nullable*/)
├── policy
│   ├── FeePolicyRepository   interface: List<FeePolicy> findAll()
│   ├── InMemoryFeePolicyRepository   생성/추가 시 전체 검증. 수정·삭제 없음.
│   ├── FeePolicyValidator    저장소 전체 검증 (effectiveFrom 중복, 기본 정책 effectiveTo 금지, 기본 정책 존재, from <= to)
│   ├── FeePolicyResolver     FeePolicy resolve(LocalDate date, String categoryId)
│   └── NoApplicablePolicyException
└── application
    └── ProfitCalculator      (Clock, FeePolicyResolver)
        ├── calculateProfitAmount(Transaction tx)                          // tx.paidAt 기준
        ├── calculateProfitAmount(Money amount, String categoryId)         // Clock의 오늘 기준
        └── calculateProfitAmount(Money amount, String categoryId, LocalDate asOf)  // 시뮬레이션
```

테스트는 `src/test/java` 아래 같은 패키지 구조로 있으며, `support.Policies`에 공용 요율표 픽스처가 있습니다.

| 테스트 | 내용 |
|---|---|
| `TieredFeeStrategyTest` | 기대값 표 4건, 경계값(500,000 / 500,001 / 1,000,000 / 1,000,001), 절사, 4구간 정책, 생성 실패 케이스 |
| `FlatFeeStrategyTest` | 고정 10%: 950,000원 → 855,000원, 내역 1행 |
| `FeePolicyResolverTest` | 버전 선택, 카테고리 우선·기간 종료 후 복귀, 정책 없음 예외 |
| `FeePolicyValidatorTest` | effectiveFrom 중복, 기본 정책 effectiveTo, 기본 정책 없음 등 |
| `ProfitCalculatorTest` | `Clock.fixed` 기반 시나리오 1·2, Clock 기준 오늘 선택, `appliedPolicyId` 추적 |
