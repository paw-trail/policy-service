package com.pawtrail.policy.domain.rule;

import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PolicyFields;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 소스들을 합친 결과입니다.
 *
 * 병합은 최종 조건만 내놓는 것이 아니라 어긋난 자리도 함께 알려 줍니다.
 * 우선순위대로 하나를 골랐다는 사실과 무엇을 버렸는지가 같은 계산에서 나오므로,
 * 둘을 따로 돌리면 같은 비교를 두 번 하게 됩니다.
 *
 * 칸마다 누가 이겼는지도 같은 까닭으로 여기 담깁니다.
 * 값을 고른 그 순회에서 승자가 정해지므로 따로 계산하면 결론이 둘이 됩니다.
 *
 * @param fields         합쳐진 조건 한 벌
 * @param hasConflict    병합이 찾은 소스 간 어긋남이 하나라도 있는지.
 *                       pet_policy.has_conflict 는 여기에 참여한 소스의 소스 내 어긋남을 더한 값이며
 *                       그 계산은 저장된 행을 봐야 해 PolicyMergeService 가 함
 * @param sourcePriority 이 병합에서 최상위로 이긴 티어
 * @param conflicts      필드별로 소스들이 뭐라고 했는지
 * @param fieldSources   칸마다 그 값을 만든 소스. 키는 조건 이름이고 순서는 FieldSpec 순서임.
 *                       값은 우선순위 순의 소스 목록이며, 아무 소스도 말하지 않은 칸은 키가 없음
 * @param participants   병합에 참여한 소스. 정정 행이 이기면 그 행 하나이고 공공이면 있는 소스 전부.
 *                       값을 하나도 안 채운 소스도 참여한 것으로 셈
 */
public record MergeResult(
        PolicyFields fields,
        boolean hasConflict,
        SourceType sourcePriority,
        List<FieldConflict> conflicts,
        Map<String, List<SourceType>> fieldSources,
        List<SourceType> participants
) {

    public MergeResult {
        if (fields == null) {
            throw new IllegalArgumentException("합쳐진 조건은 필수입니다.");
        }
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        fieldSources = copySources(fieldSources);
        participants = participants == null ? List.of() : List.copyOf(participants);
    }

    /**
     * 합칠 소스가 하나도 없을 때의 결과입니다.
     *
     * 조건을 아무것도 모르는 상태이며 판정이 UNKNOWN 으로 떨어집니다.
     * 소스가 전부 무효화된 장소에서 나옵니다.
     */
    public static MergeResult empty() {
        return new MergeResult(PolicyFields.empty(), false, null, List.of(), Map.of(), List.of());
    }

    /**
     * 승자 표를 고칠 수 없는 복사본으로 만듭니다.
     *
     * 순서를 지킵니다.
     * Map.copyOf 는 순서를 버리므로 쓰지 않습니다.
     * 병합 결과를 바로 받는 쪽(검사 · 로그)이 조건 순서로 읽기 때문입니다.
     *
     * 저장한 뒤의 순서에는 기대지 않습니다.
     * pet_policy.field_sources 는 jsonb 라 데이터베이스가 키를 다시 정렬합니다.
     * 저장된 값을 화면에 펼칠 때는 FieldSpec 순서로 다시 늘어놓아야 합니다.
     */
    private static Map<String, List<SourceType>> copySources(Map<String, List<SourceType>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<SourceType>> copied = new LinkedHashMap<>();
        source.forEach((field, sources) -> copied.put(field, List.copyOf(sources)));
        return Collections.unmodifiableMap(copied);
    }

    /**
     * 한 필드에서 소스들이 서로 다른 말을 한 자리입니다.
     *
     * policy_conflict 한 행이 되며 sourceValues 가 그대로 jsonb 로 들어갑니다.
     * 어느 쪽이 맞는지는 담지 않습니다.
     * 우선순위가 고른 값은 이미 fields 에 있고, 여기는 "무엇과 무엇이 갈렸는가" 만 남깁니다.
     *
     * @param fieldName    조건 이름. FieldSpec 의 이름 그대로이며 DB 컬럼 이름이 아님
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
