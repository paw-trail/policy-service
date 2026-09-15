package com.pawtrail.policy.domain.model;

import com.pawtrail.policy.domain.enums.BreedRule;
import com.pawtrail.policy.domain.enums.ExtraFeeUnit;
import com.pawtrail.policy.domain.enums.Scope;
import com.pawtrail.policy.domain.enums.SizeRule;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 반려동물 동반 조건 스무 가지입니다.
 *
 * pet_policy_source 와 pet_policy 가 이 값 객체를 함께 씁니다.
 * 두 표가 같은 스무 컬럼을 갖는데, 각 엔티티에 따로 선언하면 선언이 마흔 개가 되고
 * 조건을 하나 더할 때 두 곳을 고쳐야 합니다.
 * 한쪽만 고쳐도 컴파일은 통과하고 병합에서 그 필드만 조용히 빠지므로,
 * 오류가 나지 않아 드러나지 않습니다.
 *
 * 병합 엔진이 다루는 단위도 이것입니다.
 * 소스별 조건 여러 벌을 받아 최종 한 벌을 만드는 일이라
 * 엔티티가 아니라 이 값 객체를 주고받습니다.
 * 덕분에 병합 규칙을 엔티티 없이 단위 테스트로 검증할 수 있습니다.
 *
 * <b>null 과 false 는 뜻이 다릅니다.</b>
 * null 은 "정보 없음" 이고 false 는 "요구하지 않음" 입니다.
 * vaccineProof 가 null 이면 접종 증명이 필요한지 모른다는 뜻이고,
 * false 면 필요 없다고 원문이 밝힌 것입니다.
 * 판정이 이것으로 갈리므로 기본값을 주지 않고 원시 타입을 쓰지 않습니다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyFields {

    // 장소 전체가 되는지 일부 구역만 되는지임
    // 판정의 첫 축이라 여기가 갈리면 나머지를 다 만족해도 결과가 달라짐
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", length = 10)
    private Scope scope;

    // 안내견만 가능한 곳임
    //
    // 키워드 매칭이 못 잡는 함정임
    // "안내견 동반 가능" 이라고만 적혀 있고 불가라는 글자가 없어
    // 문장에서 낱말만 찾으면 동반 가능으로 읽힘
    @Column(name = "guide_dog_only")
    private Boolean guideDogOnly;

    // 반려견이 없으면 입장할 수 없는 곳임
    // 루파니애견캠핑장처럼 실재함
    @Column(name = "pet_only")
    private Boolean petOnly;

    @Column(name = "indoor_allowed")
    private Boolean indoorAllowed;

    @Column(name = "outdoor_allowed")
    private Boolean outdoorAllowed;

    // 체중 상한임
    //
    // double 이 아니라 BigDecimal 인 것은 DB 가 numeric(5,2) 이고
    // 경계값 비교가 판정을 뒤집기 때문임
    // 10kg 이하인 곳에 10.0kg 인 아이가 부동소수 오차로 떨어지면 안 됨
    @Column(name = "max_weight_kg", precision = 5, scale = 2)
    private BigDecimal maxWeightKg;

    // 상한값을 포함하는지임
    // "10kg 이하" 면 true, "10kg 미만" 이면 false
    @Column(name = "weight_inclusive")
    private Boolean weightInclusive;

    // 동반 마릿수 상한임
    @Column(name = "max_count")
    private Short maxCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "size_rule", length = 20)
    private SizeRule sizeRule;

    @Enumerated(EnumType.STRING)
    @Column(name = "breed_rule", length = 20)
    private BreedRule breedRule;

    // 목줄로는 안 되고 이동장이나 유모차가 있어야 하는 곳임
    // 산이정원처럼 실재하며 준비물이 아니라 입장 조건임
    @Column(name = "carrier_required")
    private Boolean carrierRequired;

    // 목줄 착용 요구임
    // 판정을 가르지 않고 준비물 안내로만 나감
    @Column(name = "leash_required")
    private Boolean leashRequired;

    // 들어갈 수 없는 구역의 이름임
    //
    // text[] 를 매핑하려면 JdbcTypeCode 로 배열임을 알려야 함
    // 없으면 Hibernate 가 List 를 직렬화하려 들어 ddl-auto: validate 가 막음
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "excluded_zones", columnDefinition = "text[]")
    private List<String> excludedZones;

    // 그 구역에서만 동반할 수 있는 경우의 구역 이름임
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_zones_only", columnDefinition = "text[]")
    private List<String> allowedZonesOnly;

    // 동반이 안 되는 날짜나 기간임
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "excluded_days", columnDefinition = "text[]")
    private List<String> excludedDays;

    @Column(name = "extra_fee_amount")
    private Integer extraFeeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "extra_fee_unit", length = 12)
    private ExtraFeeUnit extraFeeUnit;

    // 가져가야 하는 물건임
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "required_items", columnDefinition = "text[]")
    private List<String> requiredItems;

    @Column(name = "vaccine_proof")
    private Boolean vaccineProof;

    @Column(name = "advance_inquiry")
    private Boolean advanceInquiry;

    @Builder
    private PolicyFields(Scope scope, Boolean guideDogOnly, Boolean petOnly,
                         Boolean indoorAllowed, Boolean outdoorAllowed,
                         BigDecimal maxWeightKg, Boolean weightInclusive, Short maxCount,
                         SizeRule sizeRule, BreedRule breedRule,
                         Boolean carrierRequired, Boolean leashRequired,
                         List<String> excludedZones, List<String> allowedZonesOnly,
                         List<String> excludedDays,
                         Integer extraFeeAmount, ExtraFeeUnit extraFeeUnit,
                         List<String> requiredItems,
                         Boolean vaccineProof, Boolean advanceInquiry) {
        this.scope = scope;
        this.guideDogOnly = guideDogOnly;
        this.petOnly = petOnly;
        this.indoorAllowed = indoorAllowed;
        this.outdoorAllowed = outdoorAllowed;
        this.maxWeightKg = maxWeightKg;
        this.weightInclusive = weightInclusive;
        this.maxCount = maxCount;
        this.sizeRule = sizeRule;
        this.breedRule = breedRule;
        this.carrierRequired = carrierRequired;
        this.leashRequired = leashRequired;
        this.excludedZones = copyOf(excludedZones);
        this.allowedZonesOnly = copyOf(allowedZonesOnly);
        this.excludedDays = copyOf(excludedDays);
        this.extraFeeAmount = extraFeeAmount;
        this.extraFeeUnit = extraFeeUnit;
        this.requiredItems = copyOf(requiredItems);
        this.vaccineProof = vaccineProof;
        this.advanceInquiry = advanceInquiry;
    }

    /**
     * 아무 조건도 모르는 상태를 만듭니다.
     *
     * 스무 필드가 전부 null 이며 "정보 없음" 을 뜻합니다.
     * 소스가 하나도 없는 장소를 병합했을 때의 결과가 이것입니다.
     */
    public static PolicyFields empty() {
        return PolicyFields.builder().build();
    }

    /**
     * 목록 필드를 방어 복사합니다.
     *
     * 넘겨받은 List 를 그대로 들고 있으면 바깥에서 고칠 때 이 값도 함께 바뀝니다.
     * 병합이 여러 벌을 나란히 놓고 고르는 일이라 그런 일이 실제로 생길 수 있습니다.
     *
     * 빈 목록과 null 을 구분합니다.
     * 빈 목록은 "해당 없음" 이고 null 은 "정보 없음" 이라 뜻이 다릅니다.
     */
    private static List<String> copyOf(List<String> source) {
        return source == null ? null : List.copyOf(source);
    }

    /**
     * 목록 필드를 꺼낼 때도 복사본을 줍니다.
     *
     * getter 가 내부 목록을 그대로 내보내면 받는 쪽이 고칠 수 있습니다.
     * List.copyOf 로 만든 불변 목록이라 실제로는 고쳐지지 않으나,
     * 그 사실이 호출부에 드러나지 않아 UnsupportedOperationException 으로만 알게 됩니다.
     */
    public List<String> getExcludedZones() {
        return copyOf(excludedZones);
    }

    public List<String> getAllowedZonesOnly() {
        return copyOf(allowedZonesOnly);
    }

    public List<String> getExcludedDays() {
        return copyOf(excludedDays);
    }

    public List<String> getRequiredItems() {
        return copyOf(requiredItems);
    }

    /**
     * 조건이 하나도 채워지지 않았는지 봅니다.
     *
     * 병합 결과가 이것이면 그 장소는 판정이 UNKNOWN 으로 떨어집니다.
     * 소스는 있는데 전부 비어 있는 경우와 소스 자체가 없는 경우를 가르지 않습니다.
     * 사용자에게는 둘 다 "조건을 알 수 없음" 으로 같기 때문입니다.
     */
    public boolean isEmpty() {
        return Arrays.stream(values()).allMatch(value -> value == null);
    }

    private Object[] values() {
        return new Object[]{
                scope, guideDogOnly, petOnly, indoorAllowed, outdoorAllowed,
                maxWeightKg, weightInclusive, maxCount, sizeRule, breedRule,
                carrierRequired, leashRequired, excludedZones, allowedZonesOnly,
                excludedDays, extraFeeAmount, extraFeeUnit, requiredItems,
                vaccineProof, advanceInquiry
        };
    }
}
