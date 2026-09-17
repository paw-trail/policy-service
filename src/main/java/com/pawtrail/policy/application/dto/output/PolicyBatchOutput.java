package com.pawtrail.policy.application.dto.output;

import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicy;
import java.util.List;
import java.util.UUID;

/**
 * batch 응답에서 장소 하나의 조건입니다.
 *
 * verdict 가 판정과 카드 한 줄 근거를 만드는 데 필요한 것만 담습니다.
 * sourcePriority · mergedAt · 충돌 목록은 담지 않습니다.
 * verdict 가 쓸 자리가 없고, 안 쓰는 값을 실으면 그것이 계약이 되어
 * policy 가 안쪽을 바꿀 때 발이 묶입니다.
 * 필요해지면 더해도 받는 쪽이 모르는 키를 무시하므로 깨지지 않습니다.
 *
 * @param placeId       장소 식별자
 * @param fields        조건 스무 칸. null 과 false 를 그대로 담음
 * @param hasConflict   pet_policy.has_conflict 그대로. 장소 상세가 이 값이 참일 때만 충돌 상세를 부름
 * @param policyVersion 병합 결과의 판. verdict 가 캐시한 결과가 몇 판으로 계산한 것인지 가르는 데 씀
 * @param correctionSource 정정 행이 이긴 장소면 그 출처(MANUAL · OWNER), 공공 병합이면 null.
 *                      정정이 이기면 공공 근거를 담지 않아 근거가 비는데, verdict 가 이것으로
 *                      "근거 없는 값" 과 "사람이 확인해 정한 값" 을 가르고 출처 줄을 붙임.
 *                      정정 사유는 관리자 내부 메모라 싣지 않음
 * @param evidence      칸마다 그 값을 만든 소스의 근거. 조건 순서 → 소스 순서 → 조각 번호 순
 */
public record PolicyBatchOutput(
        UUID placeId,
        PolicyFieldsOutput fields,
        boolean hasConflict,
        int policyVersion,
        SourceType correctionSource,
        List<EvidenceOutput> evidence
) {

    public static PolicyBatchOutput of(PetPolicy policy, List<EvidenceOutput> evidence) {
        return new PolicyBatchOutput(
                policy.getPlaceId(),
                PolicyFieldsOutput.from(policy.getFields()),
                policy.isHasConflict(),
                policy.getPolicyVersion(),
                correctionSourceOf(policy.getSourcePriority()),
                List.copyOf(evidence)
        );
    }

    private static SourceType correctionSourceOf(SourceType sourcePriority) {
        return sourcePriority != null && sourcePriority.isCorrection() ? sourcePriority : null;
    }
}
