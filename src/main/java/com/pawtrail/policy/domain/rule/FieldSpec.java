package com.pawtrail.policy.domain.rule;

import com.pawtrail.policy.domain.model.PolicyFields;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 조건 한 칸을 어떻게 읽고 쓰는지입니다.
 *
 * 병합 규칙은 스무 칸에 대해 같은 일을 합니다.
 * 소스들이 말한 값을 모으고, 갈렸는지 보고, 우선순위대로 하나를 고릅니다.
 * 칸마다 코드를 따로 쓰면 같은 로직이 스무 벌이 되고,
 * 조건을 하나 더할 때 그중 한 곳을 빠뜨려도 컴파일이 통과합니다.
 *
 * 그래서 칸마다 "읽는 법" 과 "쓰는 법" 만 선언해 두고 규칙은 한 번만 씁니다.
 * 새 조건이 생기면 아래 목록에 한 줄을 더하면 되고, 빠뜨리면 그 칸이 아예 병합되지 않아
 * 테스트에서 바로 드러납니다.
 *
 * <b>name 이 조건 이름의 기준입니다.</b>
 * bulk 요청의 fields · 근거의 fieldName · 충돌의 fieldName · batch 응답이 전부 이 이름을 씁니다.
 * DB 컬럼 이름(max_weight_kg)이 아니라 camelCase(maxWeightKg)입니다.
 * 컬럼 이름을 계약에 쓰면 컬럼을 바꾸는 순간 API 가 깨지기 때문입니다.
 *
 * <b>사람이 읽는 말도 여기서 만듭니다.</b>
 * 공개 충돌 목록이 칸 라벨("체중 제한")과 값 문장("10kg")을 내보냅니다.
 * 조건 스무 칸의 이름과 값은 이 서비스가 주인이라 policy 가 만들고,
 * 소스 이름은 place 가 주인이라 여기서 만들지 않습니다.
 * 칸마다 한 줄에 함께 선언하므로 조건을 더할 때 고칠 곳이 늘지 않습니다.
 *
 * @param name   조건 이름. camelCase 이며 요청 · 근거 · 충돌 · 응답이 모두 이 이름을 씀
 * @param label  화면에 보이는 칸 이름
 * @param getter 조건 한 벌에서 이 칸의 값을 꺼냄
 * @param setter 빌더에 이 칸의 값을 넣음
 * @param list   목록형인지. 목록은 포함 관계를 보고 합집합을 취하므로 규칙이 다름
 * @param sameAs 두 값이 같은지. 대부분 equals 이나 그것으로 안 되는 칸이 있음
 * @param text   값을 사람이 읽는 문장으로 바꿈. jsonb 에서 읽은 값(불리언 · 문자열 · 숫자 · 목록)을 받음
 */
public record FieldSpec<T>(
        String name,
        String label,
        Function<PolicyFields, T> getter,
        BiConsumer<PolicyFields.PolicyFieldsBuilder, T> setter,
        boolean list,
        BiPredicate<T, T> sameAs,
        Function<Object, String> text
) {

    private static <T> FieldSpec<T> of(String name, String label,
                                       Function<PolicyFields, T> getter,
                                       BiConsumer<PolicyFields.PolicyFieldsBuilder, T> setter,
                                       Function<Object, String> text) {
        return new FieldSpec<>(name, label, getter, setter, false, Objects::equals, text);
    }

    private static <T> FieldSpec<T> of(String name, String label,
                                       Function<PolicyFields, T> getter,
                                       BiConsumer<PolicyFields.PolicyFieldsBuilder, T> setter,
                                       BiPredicate<T, T> sameAs,
                                       Function<Object, String> text) {
        return new FieldSpec<>(name, label, getter, setter, false, sameAs, text);
    }

    private static FieldSpec<List<String>> listOf(
            String name, String label,
            Function<PolicyFields, List<String>> getter,
            BiConsumer<PolicyFields.PolicyFieldsBuilder, List<String>> setter) {
        return new FieldSpec<>(name, label, getter, setter, true, Objects::equals, joined());
    }

    /**
     * 체중 상한이 같은 값인지 봅니다.
     *
     * BigDecimal 은 10 과 10.00 을 equals 로 다르게 봅니다.
     * 소스마다 "10kg" 과 "10.0kg" 처럼 표기가 갈리므로 그대로 비교하면
     * 없는 충돌이 잡히고 사용자에게 "출처에 따라 조건이 다릅니다" 가 잘못 뜹니다.
     *
     * 병합에서 닫는 이유는 여기가 비교가 일어나는 유일한 자리이기 때문입니다.
     * 적재할 때 자릿수를 맞추는 방법도 있으나 경로가 셋이고(extract · 관리자 정정 · 재병합)
     * 병합은 저장 전 값끼리도 비교하므로 그쪽에서는 닫히지 않습니다.
     */
    private static boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    /**
     * 조건 스무 칸 전부입니다.
     *
     * 순서는 DB 컬럼 순서와 같습니다.
     * 관리자 화면이 정정 전후를 이 순서로 펼쳐 보여주므로,
     * 섞이면 같은 정정인데 매번 다르게 보입니다.
     */
    public static final List<FieldSpec<?>> ALL = List.of(
            of("scope", "동반 범위", PolicyFields::getScope,
                    PolicyFields.PolicyFieldsBuilder::scope,
                    named(Map.of("ALL_AREA", "전 구역", "PARTIAL", "일부 구역", "NONE", "동반 불가",
                            "UNKNOWN", "알 수 없음"))),
            of("guideDogOnly", "안내견 한정", PolicyFields::getGuideDogOnly,
                    PolicyFields.PolicyFieldsBuilder::guideDogOnly,
                    yesNo("안내견만 가능", "안내견 외에도 가능")),
            of("petOnly", "반려견 동반 전용", PolicyFields::getPetOnly,
                    PolicyFields.PolicyFieldsBuilder::petOnly,
                    yesNo("반려견과 함께만 입장", "반려견 없이도 입장")),
            of("indoorAllowed", "실내 동반", PolicyFields::getIndoorAllowed,
                    PolicyFields.PolicyFieldsBuilder::indoorAllowed,
                    yesNo("가능", "불가")),
            of("outdoorAllowed", "실외 동반", PolicyFields::getOutdoorAllowed,
                    PolicyFields.PolicyFieldsBuilder::outdoorAllowed,
                    yesNo("가능", "불가")),
            of("maxWeightKg", "체중 제한", PolicyFields::getMaxWeightKg,
                    PolicyFields.PolicyFieldsBuilder::maxWeightKg, FieldSpec::sameAmount,
                    numberWith("kg")),
            of("weightInclusive", "체중 기준", PolicyFields::getWeightInclusive,
                    PolicyFields.PolicyFieldsBuilder::weightInclusive,
                    yesNo("이하", "미만")),
            of("maxCount", "마릿수 제한", PolicyFields::getMaxCount,
                    PolicyFields.PolicyFieldsBuilder::maxCount,
                    numberWith("마리까지")),
            of("sizeRule", "크기 제한", PolicyFields::getSizeRule,
                    PolicyFields.PolicyFieldsBuilder::sizeRule,
                    named(Map.of("SMALL_ONLY", "소형견만", "SMALL_MEDIUM", "소형 · 중형견", "ALL", "제한 없음"))),
            of("breedRule", "견종 제한", PolicyFields::getBreedRule,
                    PolicyFields.PolicyFieldsBuilder::breedRule,
                    named(Map.of("NONE", "제한 없음", "DANGEROUS_MUZZLE", "맹견은 입마개 착용",
                            "DANGEROUS_BANNED", "맹견 불가"))),
            of("carrierRequired", "이동장", PolicyFields::getCarrierRequired,
                    PolicyFields.PolicyFieldsBuilder::carrierRequired,
                    yesNo("필요", "필요 없음")),
            of("leashRequired", "목줄", PolicyFields::getLeashRequired,
                    PolicyFields.PolicyFieldsBuilder::leashRequired,
                    yesNo("필요", "필요 없음")),
            listOf("excludedZones", "동반 불가 구역", PolicyFields::getExcludedZones,
                    PolicyFields.PolicyFieldsBuilder::excludedZones),
            listOf("allowedZonesOnly", "동반 가능 구역", PolicyFields::getAllowedZonesOnly,
                    PolicyFields.PolicyFieldsBuilder::allowedZonesOnly),
            listOf("excludedDays", "동반 불가일", PolicyFields::getExcludedDays,
                    PolicyFields.PolicyFieldsBuilder::excludedDays),
            of("extraFeeAmount", "추가 요금", PolicyFields::getExtraFeeAmount,
                    PolicyFields.PolicyFieldsBuilder::extraFeeAmount,
                    won()),
            of("extraFeeUnit", "요금 기준", PolicyFields::getExtraFeeUnit,
                    PolicyFields.PolicyFieldsBuilder::extraFeeUnit,
                    named(Map.of("PER_DOG", "마리당", "PER_NIGHT", "1박당", "PER_VISIT", "방문당"))),
            listOf("requiredItems", "준비물", PolicyFields::getRequiredItems,
                    PolicyFields.PolicyFieldsBuilder::requiredItems),
            of("vaccineProof", "접종 증명", PolicyFields::getVaccineProof,
                    PolicyFields.PolicyFieldsBuilder::vaccineProof,
                    yesNo("필요", "필요 없음")),
            of("advanceInquiry", "사전 문의", PolicyFields::getAdvanceInquiry,
                    PolicyFields.PolicyFieldsBuilder::advanceInquiry,
                    yesNo("필요", "필요 없음"))
    );

    /**
     * 조건 이름에서 위 목록의 순서로 가는 표입니다.
     *
     * ALL 보다 아래에 있어야 합니다.
     * 정적 필드는 적힌 순서대로 초기화되므로 위에 두면 ALL 이 아직 비어 있습니다.
     */
    private static final Map<String, Integer> ORDER = indexByName();

    /**
     * 조건 이름으로 명세를 찾는 표입니다.
     *
     * ORDER 와 같은 까닭으로 ALL 보다 아래에 있어야 합니다.
     */
    private static final Map<String, FieldSpec<?>> BY_NAME = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(FieldSpec::name, Function.identity()));

    /**
     * 조건 스무 칸 중 하나의 이름인지 봅니다.
     *
     * bulk 요청이 근거와 소스 내 충돌의 이름을 이것으로 막습니다.
     * 막지 않으면 오타가 그대로 저장되고, batch 가 조건과 근거를 이름으로 이을 때
     * 오류 없이 그 칸의 근거만 사라집니다.
     */
    public static boolean isKnownName(String name) {
        return name != null && ORDER.containsKey(name);
    }

    /**
     * 그 조건이 목록에서 몇 번째인지입니다.
     *
     * batch 가 근거를 조건 순서로 늘어놓을 때 씁니다.
     * 모르는 이름은 맨 뒤로 보냅니다.
     * bulk 가 막으므로 새로 쌓이지는 않으나, 막기 전에 들어간 행이 있을 수 있어
     * 예외를 내지 않고 뒤에 둡니다.
     */
    public static int orderOf(String name) {
        Integer order = name == null ? null : ORDER.get(name);
        return order == null ? Integer.MAX_VALUE : order;
    }

    private static Map<String, Integer> indexByName() {
        Map<String, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < ALL.size(); i++) {
            order.put(ALL.get(i).name(), i);
        }
        return Map.copyOf(order);
    }

    /**
     * 조건 한 벌을 칸 이름과 값의 맵으로 옮깁니다.
     *
     * 정정 이력의 전후 스냅샷이 씁니다.
     * 비어 있는 칸도 키를 남깁니다. 정정은 전체 교체라
     * "무엇을 안 건드렸나" 가 이력에서 사라지면 안 되기 때문입니다.
     * 맵의 순서는 조건 순서이나 jsonb 에 넣으면 키 순서가 사라지므로
     * 화면에 펼칠 때는 이 목록 순서로 다시 늘어놓아야 합니다.
     */
    public static Map<String, Object> snapshotOf(PolicyFields fields) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (FieldSpec<?> spec : ALL) {
            snapshot.put(spec.name(), spec.getter().apply(fields));
        }
        return snapshot;
    }

    /**
     * 조건 이름으로 명세를 찾습니다.
     */
    public static Optional<FieldSpec<?>> find(String name) {
        return Optional.ofNullable(name == null ? null : BY_NAME.get(name));
    }

    /**
     * 화면에 보일 칸 이름입니다.
     *
     * 모르는 이름이면 받은 이름을 그대로 돌려줍니다.
     * bulk 가 막으므로 새로 쌓이지 않으나, 막기 전에 들어간 행 때문에 화면을 죽이지 않습니다.
     */
    public static String labelOf(String name) {
        return find(name).map(FieldSpec::label).orElse(name);
    }

    /**
     * 그 칸의 값을 사람이 읽는 문장으로 바꿉니다.
     *
     * 값은 jsonb 에서 읽은 모양(불리언 · 문자열 · 숫자 · 목록)이어도 되고
     * 메모리의 모양(열거값 · BigDecimal)이어도 됩니다. 둘 다 같은 문장이 됩니다.
     * 모르는 칸이면 값을 그대로 문자열로 바꿉니다.
     */
    public static String textOf(String name, Object value) {
        if (value == null) {
            return null;
        }
        return find(name).map(spec -> spec.text().apply(value)).orElseGet(() -> String.valueOf(value));
    }

    // ── 값을 문장으로 바꾸는 법 ────────────────────────────────────────────

    private static Function<Object, String> yesNo(String yes, String no) {
        return value -> value instanceof Boolean flag ? (flag ? yes : no) : String.valueOf(value);
    }

    // 열거값은 메모리에서는 열거 상수, jsonb 에서는 이름 문자열이라 이름으로 찾음
    private static Function<Object, String> named(Map<String, String> names) {
        return value -> names.getOrDefault(String.valueOf(value), String.valueOf(value));
    }

    private static Function<Object, String> numberWith(String suffix) {
        return value -> plainNumber(value) + suffix;
    }

    private static Function<Object, String> won() {
        return value -> value instanceof Number number
                ? String.format(Locale.KOREA, "%,d원", number.longValue())
                : String.valueOf(value);
    }

    private static Function<Object, String> joined() {
        return value -> {
            if (value instanceof Collection<?> items) {
                return items.isEmpty()
                        ? "없음"
                        : items.stream().map(String::valueOf).collect(Collectors.joining(", "));
            }
            return String.valueOf(value);
        };
    }

    /**
     * 숫자를 뒷자리 0 없이 적습니다.
     *
     * jsonb 에서 읽으면 10.00 이 10.0 으로 오고, 메모리에서는 BigDecimal 10.00 입니다.
     * 둘 다 "10" 이 되어야 같은 값이 같은 문장이 됩니다.
     * toPlainString 을 쓰는 것은 stripTrailingZeros 가 10 을 1E+1 로 만들기 때문입니다.
     */
    private static String plainNumber(Object value) {
        if (value instanceof Number) {
            try {
                return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                return value.toString();
            }
        }
        return String.valueOf(value);
    }
}
