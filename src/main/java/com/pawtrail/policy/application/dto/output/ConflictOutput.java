package com.pawtrail.policy.application.dto.output;

import com.pawtrail.policy.domain.enums.ConflictType;
import com.pawtrail.policy.domain.enums.IntraConflictKey;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PolicyConflict;
import com.pawtrail.policy.domain.rule.FieldSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 공개 충돌 목록의 한 줄입니다.
 *
 * <b>사람이 읽는 말은 어휘의 주인이 만듭니다.</b>
 * 조건 라벨과 값 문장은 조건의 주인인 이 서비스가 FieldSpec 으로 만들고,
 * 소스 이름은 place 가 주인이라 코드만 보냅니다.
 * 화면은 함께 받는 장소 상세의 sources[] 로 소스 이름을 붙입니다.
 * 두 곳에서 이름을 만들면 같은 화면에서 출처 뱃지와 충돌 표가 다른 이름을 띄울 수 있습니다.
 *
 * <b>소스 간 어긋남</b>은 소스 열거 순서(PET_TOUR → GOCAMPING → CULTURE_CSV)로
 * 소스마다 한 자리를 내보내고 값을 문장으로 바꿉니다.
 * <b>소스 내 어긋남</b>은 같은 소스로 "항목 값" → "본문" 두 자리를 내보내고
 * 값은 원문 문자열 그대로 둡니다. 원문의 말을 옮긴 것이라 문장으로 바꿀 대상이 아닙니다.
 * jsonb 가 키 순서를 버리므로 저장된 순서에 기대지 않고 여기서 다시 늘어놓습니다.
 *
 * @param fieldName    조건 이름
 * @param label        화면에 보이는 조건 이름
 * @param conflictType 소스 간인지 소스 내인지
 * @param sourceValues 자리마다 무엇이라고 했는지
 */
public record ConflictOutput(
        String fieldName,
        String label,
        ConflictType conflictType,
        List<ConflictValueOutput> sourceValues
) {

    public static ConflictOutput from(PolicyConflict conflict) {
        List<ConflictValueOutput> values = conflict.getConflictType() == ConflictType.INTRA_SOURCE
                ? intraSourceValues(conflict)
                : crossSourceValues(conflict);
        return new ConflictOutput(
                conflict.getFieldName(),
                FieldSpec.labelOf(conflict.getFieldName()),
                conflict.getConflictType(),
                values
        );
    }

    // 키가 소스 이름이고 값이 그 소스가 말한 원값임
    private static List<ConflictValueOutput> crossSourceValues(PolicyConflict conflict) {
        Map<String, Object> stated = conflict.getSourceValues();
        List<ConflictValueOutput> values = new ArrayList<>();
        for (SourceType source : SourceType.values()) {
            Object value = stated.get(source.name());
            if (value != null) {
                values.add(new ConflictValueOutput(source, null,
                        FieldSpec.textOf(conflict.getFieldName(), value)));
            }
        }
        return List.copyOf(values);
    }

    // 키가 field · text 이고 값이 원문 문자열임
    // 정해진 두 키 밖의 키는 담지 않음 — bulk 가 막으므로 새로 쌓이지 않고
    // 막기 전에 들어간 행의 원문 키 이름이 화면에 뜨지 않게 함
    private static List<ConflictValueOutput> intraSourceValues(PolicyConflict conflict) {
        Map<String, Object> stated = conflict.getSourceValues();
        List<ConflictValueOutput> values = new ArrayList<>();
        for (IntraConflictKey key : IntraConflictKey.values()) {
            Object value = stated.get(key.key());
            if (value != null) {
                values.add(new ConflictValueOutput(conflict.getSource(), key.label(),
                        String.valueOf(value)));
            }
        }
        return List.copyOf(values);
    }
}
