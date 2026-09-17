package com.pawtrail.policy.presentation.request;

import com.pawtrail.policy.domain.enums.BreedRule;
import com.pawtrail.policy.domain.enums.ExtraFeeUnit;
import com.pawtrail.policy.domain.enums.Scope;
import com.pawtrail.policy.domain.enums.SizeRule;
import com.pawtrail.policy.domain.model.PolicyFields;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

/**
 * extract 가 뽑은 조건 한 벌입니다.
 *
 * 값 객체를 그대로 받지 않고 이 DTO 를 두는 이유는 엔티티가 HTTP 계약이 되지 않게 하기
 * 위해서입니다. 그대로 받으면 컬럼 이름을 바꾸는 순간 API 가 깨지고,
 * 검증 애너테이션도 값 객체에 붙게 됩니다.
 *
 * <b>비어 있음과 false 는 뜻이 다릅니다.</b>
 * 비어 있으면 "정보 없음" 이고 false 는 "요구하지 않음" 입니다.
 * 그래서 어느 칸에도 필수를 걸지 않고 원시 타입을 쓰지 않습니다.
 * extract 가 원문에서 못 찾은 칸은 비워서 보냅니다.
 *
 * <b>조건을 하나 더할 때 고칠 곳이 다섯입니다.</b>
 * 마이그레이션, PolicyFields, FieldSpec, 여기, 그리고 batch 응답의 PolicyFieldsOutput 입니다.
 * 여기를 빠뜨리면 컴파일은 통과하고 그 칸만 조용히 안 들어오므로,
 * 적재 테스트에서 스무 칸이 다 찬 요청을 보내 저장된 값을 대조합니다.
 * 나가는 쪽도 같아서 batch 테스트가 스무 칸이 다 찬 행을 읽어 응답을 대조합니다.
 */
public record PolicyFieldsRequest(

        Scope scope,
        Boolean guideDogOnly,
        Boolean petOnly,
        Boolean indoorAllowed,
        Boolean outdoorAllowed,

        // 소수 둘째 자리까지임. DB 가 numeric(5,2) 라 그 이상은 잘림
        @DecimalMin(value = "0.0", inclusive = false, message = "체중 상한은 0보다 커야 합니다.")
        @Digits(integer = 3, fraction = 2, message = "체중 상한의 자릿수가 맞지 않습니다.")
        BigDecimal maxWeightKg,

        Boolean weightInclusive,

        @Min(value = 1, message = "동반 마릿수 상한은 1 이상이어야 합니다.")
        Short maxCount,

        SizeRule sizeRule,
        BreedRule breedRule,
        Boolean carrierRequired,
        Boolean leashRequired,

        List<String> excludedZones,
        List<String> allowedZonesOnly,
        List<String> excludedDays,

        @PositiveOrZero(message = "추가 요금은 0 이상이어야 합니다.")
        Integer extraFeeAmount,

        ExtraFeeUnit extraFeeUnit,
        List<String> requiredItems,
        Boolean vaccineProof,
        Boolean advanceInquiry
) {

    /**
     * 도메인의 값 객체로 옮깁니다.
     *
     * 목록은 값 객체가 방어 복사하므로 여기서 손대지 않습니다.
     */
    public PolicyFields toPolicyFields() {
        return PolicyFields.builder()
                .scope(scope)
                .guideDogOnly(guideDogOnly)
                .petOnly(petOnly)
                .indoorAllowed(indoorAllowed)
                .outdoorAllowed(outdoorAllowed)
                .maxWeightKg(maxWeightKg)
                .weightInclusive(weightInclusive)
                .maxCount(maxCount)
                .sizeRule(sizeRule)
                .breedRule(breedRule)
                .carrierRequired(carrierRequired)
                .leashRequired(leashRequired)
                .excludedZones(excludedZones)
                .allowedZonesOnly(allowedZonesOnly)
                .excludedDays(excludedDays)
                .extraFeeAmount(extraFeeAmount)
                .extraFeeUnit(extraFeeUnit)
                .requiredItems(requiredItems)
                .vaccineProof(vaccineProof)
                .advanceInquiry(advanceInquiry)
                .build();
    }
}
