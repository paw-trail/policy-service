package com.pawtrail.policy.domain.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * jsonb 컬럼에 담을 값을 안전한 복사본으로 만듭니다.
 *
 * 조건 스무 가지를 그대로 찍어 두는 자리가 둘 있습니다.
 * 어긋난 값(policy_conflict.source_values)과 정정 전후(policy_correction_log)입니다.
 * 둘 다 "그때 이랬다" 를 담는 것이라 나중에 바뀌면 기록의 뜻이 사라집니다.
 *
 * <b>Map.copyOf 를 쓸 수 없습니다.</b>
 * 두 가지 이유가 겹칩니다.
 *
 * 첫째, null 값을 허용하지 않아 NullPointerException 이 납니다.
 * PolicyFields 는 null 을 "정보 없음" 으로 쓰므로 스무 필드 중 상당수가 null 입니다.
 * 조건을 모르는 장소를 정정하면 그 자리에서 터집니다.
 *
 * 둘째, 바깥 맵만 복사해 중첩된 List 와 Map 은 원본과 같은 것을 가리킵니다.
 * excluded_zones 처럼 목록인 필드가 넷이라 실제로 걸립니다.
 */
public final class JsonSnapshot {

    private JsonSnapshot() {
    }

    /**
     * 중첩된 목록과 맵까지 복사해 고칠 수 없는 맵으로 돌려줍니다.
     *
     * null 값은 그대로 둡니다.
     * "정보 없음" 이 이 스냅샷에서 지워지면 안 되기 때문입니다.
     *
     * 키 순서는 이 복사본 안에서만 지킵니다.
     * jsonb 로 저장하면 PostgreSQL 이 키를 다시 정렬하므로 DB 에서 읽은 값은 순서가 섞여 있습니다.
     * 사람이 읽는 순서로 늘어놓아야 하는 곳에서는 읽은 뒤 FieldSpec 순서로 다시 정렬합니다.
     */
    public static Map<String, Object> deepCopy(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> copied = new LinkedHashMap<>();
        source.forEach((key, value) -> copied.put(key, copyValue(value)));
        return Collections.unmodifiableMap(copied);
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            return deepCopy((Map<String, Object>) nested);
        }
        if (value instanceof List<?> nested) {
            List<Object> items = new ArrayList<>(nested.size());
            nested.forEach(item -> items.add(copyValue(item)));
            return Collections.unmodifiableList(items);
        }
        // 나머지는 문자열 · 숫자 · 불리언 · null 이라 그 자체가 불변임
        // BigDecimal 도 불변이라 복사가 필요 없음
        return value;
    }
}
