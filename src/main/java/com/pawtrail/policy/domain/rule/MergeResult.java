package com.pawtrail.policy.domain.rule;

import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PolicyFields;
import java.util.List;
import java.util.Map;

/**
 * 소스들을 합친 결과입니다.
 *
 * 병합은 최종 조건만 내놓는 것이 아니라 어긋난 자리도 함께 알려 줍니다.
 * 우선순위대로 하나를 골랐다는 사실과 무엇을 버렸는지가 같은 계산에서 나오므로,
 * 둘을 따로 돌리면 같은 비교를 두 번 하게 됩니다.
 *
 * @param fields         합쳐진 조건 한 벌
 * @param hasConflict    어긋난 자리가 하나라도 있는지
 * @param sourcePriority 이 병합에서 최상위로 이긴 티어
 * @param conflicts      필드별로 소스들이 뭐라고 했는지
 */
public record MergeResult(
        PolicyFields fields,
        boolean hasConflict,
        SourceType sourcePriority,
        List<FieldConflict> conflicts
) {

    public MergeResult {
        if (fields == null) {
            throw new IllegalArgumentException("합쳐진 조건은 필수입니다.");
        }
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    /**
     * 합칠 소스가 하나도 없을 때의 결과입니다.
     *
     * 조건을 아무것도 모르는 상태이며 판정이 UNKNOWN 으로 떨어집니다.
     * 소스가 전부 무효화된 장소에서 나옵니다.
     */
    public static MergeResult empty() {
        return new MergeResult(PolicyFields.empty(), false, null, List.of());
    }

    /**
     * 한 필드에서 소스들이 서로 다른 말을 한 자리입니다.
     *
     * policy_conflict 한 행이 되며 sourceValues 가 그대로 jsonb 로 들어갑니다.
     * 어느 쪽이 맞는지는 담지 않습니다.
     * 우선순위가 고른 값은 이미 fields 에 있고, 여기는 "무엇과 무엇이 갈렸는가" 만 남깁니다.
     *
     * @param fieldName    pet_policy 의 컬럼 이름
     * @param sourceValues 소스 이름을 키로 한 값. 순서를 지켜야 관리자 화면이 일정하게 보임
     */
    public record FieldConflict(String fieldName, Map<String, Object> sourceValues) {

        public FieldConflict {
            if (fieldName == null || fieldName.isBlank()) {
                throw new IllegalArgumentException("fieldName 은 필수입니다.");
            }
            if (sourceValues == null || sourceValues.size() < 2) {
                throw new IllegalArgumentException("어긋남은 값이 둘 이상이어야 합니다.");
            }
        }
    }
}
