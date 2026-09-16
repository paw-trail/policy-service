package com.pawtrail.policy.application.dto.output;

import com.pawtrail.policy.domain.enums.BreedRule;
import com.pawtrail.policy.domain.enums.ExtraFeeUnit;
import com.pawtrail.policy.domain.enums.Scope;
import com.pawtrail.policy.domain.enums.SizeRule;
import com.pawtrail.policy.domain.model.PolicyFields;
import java.math.BigDecimal;
import java.util.List;

/**
 * 조건 한 벌을 내보내는 모양입니다.
 *
 * 값 객체를 그대로 내보내지 않는 이유는 PolicyFieldsRequest 와 같습니다.
 * 엔티티가 HTTP 계약이 되지 않게 하려는 것이며, 그대로 내보내면 값 객체의 필드를 바꾸는 순간
 * verdict 가 받는 모양이 함께 바뀝니다.
 *
 * 칸 이름은 bulk 요청의 fields 와 같습니다.
 * 들어오는 이름과 나가는 이름이 같아야 extract 와 verdict 가 같은 칸을 같은 이름으로 부릅니다.
 *
 * <b>비어 있음과 false 를 그대로 내보냅니다.</b>
 * null 은 "정보 없음", false 는 "요구하지 않음" 이라 판정이 갈립니다.
 * 원시 타입을 쓰지 않으므로 null 이 false 로 바뀌지 않습니다.
 *
 * <b>조건을 하나 더할 때 여기도 고쳐야 합니다.</b>
 * 빠뜨리면 컴파일은 통과하고 그 칸만 응답에서 조용히 빠지므로,
 * batch 검사가 스무 칸이 다 찬 행을 읽어 응답을 대조합니다.
 */
public record PolicyFieldsOutput(
        Scope scope,
        Boolean guideDogOnly,
        Boolean petOnly,
        Boolean indoorAllowed,
        Boolean outdoorAllowed,
        BigDecimal maxWeightKg,
        Boolean weightInclusive,
        Short maxCount,
        SizeRule sizeRule,
        BreedRule breedRule,
        Boolean carrierRequired,
        Boolean leashRequired,
        List<String> excludedZones,
        List<String> allowedZonesOnly,
        List<String> excludedDays,
        Integer extraFeeAmount,
        ExtraFeeUnit extraFeeUnit,
        List<String> requiredItems,
        Boolean vaccineProof,
        Boolean advanceInquiry
) {

    public static PolicyFieldsOutput from(PolicyFields fields) {
        return new PolicyFieldsOutput(
                fields.getScope(),
                fields.getGuideDogOnly(),
                fields.getPetOnly(),
                fields.getIndoorAllowed(),
                fields.getOutdoorAllowed(),
                fields.getMaxWeightKg(),
                fields.getWeightInclusive(),
                fields.getMaxCount(),
                fields.getSizeRule(),
                fields.getBreedRule(),
                fields.getCarrierRequired(),
                fields.getLeashRequired(),
                fields.getExcludedZones(),
                fields.getAllowedZonesOnly(),
                fields.getExcludedDays(),
                fields.getExtraFeeAmount(),
                fields.getExtraFeeUnit(),
                fields.getRequiredItems(),
                fields.getVaccineProof(),
                fields.getAdvanceInquiry()
        );
    }
}
