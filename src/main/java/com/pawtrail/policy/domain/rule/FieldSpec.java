package com.pawtrail.policy.domain.rule;

import com.pawtrail.policy.domain.model.PolicyFields;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

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
 * @param name   pet_policy 의 컬럼 이름. 충돌 기록과 changedFields 에 그대로 쓰임
 * @param getter 조건 한 벌에서 이 칸의 값을 꺼냄
 * @param setter 빌더에 이 칸의 값을 넣음
 * @param list   목록형인지. 목록은 포함 관계를 보고 합집합을 취하므로 규칙이 다름
 * @param sameAs 두 값이 같은지. 대부분 equals 이나 그것으로 안 되는 칸이 있음
 */
public record FieldSpec<T>(
        String name,
        Function<PolicyFields, T> getter,
        BiConsumer<PolicyFields.PolicyFieldsBuilder, T> setter,
        boolean list,
        BiPredicate<T, T> sameAs
) {

    private static <T> FieldSpec<T> of(String name,
                                       Function<PolicyFields, T> getter,
                                       BiConsumer<PolicyFields.PolicyFieldsBuilder, T> setter) {
        return new FieldSpec<>(name, getter, setter, false, Objects::equals);
    }

    private static <T> FieldSpec<T> of(String name,
                                       Function<PolicyFields, T> getter,
                                       BiConsumer<PolicyFields.PolicyFieldsBuilder, T> setter,
                                       BiPredicate<T, T> sameAs) {
        return new FieldSpec<>(name, getter, setter, false, sameAs);
    }

    private static FieldSpec<List<String>> listOf(
            String name,
            Function<PolicyFields, List<String>> getter,
            BiConsumer<PolicyFields.PolicyFieldsBuilder, List<String>> setter) {
        return new FieldSpec<>(name, getter, setter, true, Objects::equals);
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
            of("scope", PolicyFields::getScope,
                    PolicyFields.PolicyFieldsBuilder::scope),
            of("guideDogOnly", PolicyFields::getGuideDogOnly,
                    PolicyFields.PolicyFieldsBuilder::guideDogOnly),
            of("petOnly", PolicyFields::getPetOnly,
                    PolicyFields.PolicyFieldsBuilder::petOnly),
            of("indoorAllowed", PolicyFields::getIndoorAllowed,
                    PolicyFields.PolicyFieldsBuilder::indoorAllowed),
            of("outdoorAllowed", PolicyFields::getOutdoorAllowed,
                    PolicyFields.PolicyFieldsBuilder::outdoorAllowed),
            of("maxWeightKg", PolicyFields::getMaxWeightKg,
                    PolicyFields.PolicyFieldsBuilder::maxWeightKg, FieldSpec::sameAmount),
            of("weightInclusive", PolicyFields::getWeightInclusive,
                    PolicyFields.PolicyFieldsBuilder::weightInclusive),
            of("maxCount", PolicyFields::getMaxCount,
                    PolicyFields.PolicyFieldsBuilder::maxCount),
            of("sizeRule", PolicyFields::getSizeRule,
                    PolicyFields.PolicyFieldsBuilder::sizeRule),
            of("breedRule", PolicyFields::getBreedRule,
                    PolicyFields.PolicyFieldsBuilder::breedRule),
            of("carrierRequired", PolicyFields::getCarrierRequired,
                    PolicyFields.PolicyFieldsBuilder::carrierRequired),
            of("leashRequired", PolicyFields::getLeashRequired,
                    PolicyFields.PolicyFieldsBuilder::leashRequired),
            listOf("excludedZones", PolicyFields::getExcludedZones,
                    PolicyFields.PolicyFieldsBuilder::excludedZones),
            listOf("allowedZonesOnly", PolicyFields::getAllowedZonesOnly,
                    PolicyFields.PolicyFieldsBuilder::allowedZonesOnly),
            listOf("excludedDays", PolicyFields::getExcludedDays,
                    PolicyFields.PolicyFieldsBuilder::excludedDays),
            of("extraFeeAmount", PolicyFields::getExtraFeeAmount,
                    PolicyFields.PolicyFieldsBuilder::extraFeeAmount),
            of("extraFeeUnit", PolicyFields::getExtraFeeUnit,
                    PolicyFields.PolicyFieldsBuilder::extraFeeUnit),
            listOf("requiredItems", PolicyFields::getRequiredItems,
                    PolicyFields.PolicyFieldsBuilder::requiredItems),
            of("vaccineProof", PolicyFields::getVaccineProof,
                    PolicyFields.PolicyFieldsBuilder::vaccineProof),
            of("advanceInquiry", PolicyFields::getAdvanceInquiry,
                    PolicyFields.PolicyFieldsBuilder::advanceInquiry)
    );
}
