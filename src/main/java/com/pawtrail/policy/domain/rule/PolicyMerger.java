package com.pawtrail.policy.domain.rule;

import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.model.PolicyFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 소스별 조건을 한 벌로 합칩니다.
 *
 * 스프링을 모르는 순수 계산이며 소스 목록을 인자로 받습니다.
 * 조회와 저장은 PolicyMergeService 가 맡습니다.
 * 나눠 둔 덕에 엔티티를 저장하지 않고도 규칙을 단위 테스트로 검증할 수 있습니다.
 *
 * <b>병합은 세 단계입니다.</b>
 * <pre>
 * ① OWNER 행이 있으면          그 행이 통째로 최종본이 됨
 * ② 없고 MANUAL 행이 있으면     그 행이 통째로 됨
 * ③ 둘 다 없으면               공공 3종을 필드 단위로 합침
 * </pre>
 *
 * ①② 가 통째로 이기는 것은 그 행이 "사람이 확인한 조건 한 벌" 이기 때문입니다.
 * 관리자 화면이 현재 값을 폼에 채워 보여주고 저장할 때 스무 값을 전부 보내므로,
 * 비어 있는 칸도 "안 건드린 칸" 이 아니라 "정보 없음으로 판단한 칸" 입니다.
 * 여기서 공공 값을 끌어올리면 그 판단이 뒤집힙니다.
 *
 * ③ 이 필드 단위인 것은 소스마다 채우는 칸이 다르기 때문입니다.
 * 한 소스를 통째로 쓰면 나머지가 채웠을 칸이 전부 비고, 비어 있는 값은 "정보 없음" 이라
 * 판정이 UNKNOWN 으로 떨어집니다. 가진 근거를 버려서 모른다고 답하는 셈입니다.
 *
 * <b>칸마다 누가 이겼는지도 함께 적습니다.</b>
 * batch 조회가 그것으로 근거를 거릅니다. 근거 표는 소스마다 제 근거를 가지므로
 * 병합에서 진 소스의 근거도 남아 있고, 칸 이름으로만 고르면 정정한 값 옆에
 * 옛 공공 문구가 출처로 뜹니다.
 * <pre>
 * ①② 정정 행이 이기면   스무 칸 전부 그 소스
 * ③ 값 칸               처음 값을 말한 소스
 * ③ 목록 칸             합집합을 쌓을 때 새 원소를 하나라도 보탠 소스
 * </pre>
 */
public final class PolicyMerger {

    /**
     * 공공 소스 사이의 순서입니다.
     *
     * PET_TOUR 가 앞인 것은 셋 중 동반 조건을 본업으로 가진 유일한 데이터셋이기 때문입니다.
     * 조건이 정형 필드로 들어 있고 나머지 둘은 부대 정보에 섞여 있습니다.
     *
     * 이 순서가 틀려도 되돌릴 수 있습니다.
     * 값이 갈리면 충돌로 기록되고 관리자가 정정하면 그것이 이깁니다.
     * 기본값이지 최종 판단이 아닙니다.
     */
    private static final List<SourceType> PUBLIC_PRIORITY = List.of(
            SourceType.PET_TOUR,
            SourceType.GOCAMPING,
            SourceType.CULTURE_CSV
    );

    private PolicyMerger() {
    }

    /**
     * 이 장소의 소스들을 합칩니다.
     *
     * 소스가 하나도 없으면 아무것도 모르는 결과를 돌려줍니다.
     * 소스가 전부 무효화된 장소에서 나오며, 판정은 UNKNOWN 이 됩니다.
     */
    public static MergeResult merge(List<PetPolicySource> sources) {
        if (sources == null || sources.isEmpty()) {
            return MergeResult.empty();
        }

        PetPolicySource owner = findBySource(sources, SourceType.OWNER);
        if (owner != null) {
            return single(owner);
        }

        PetPolicySource manual = findBySource(sources, SourceType.MANUAL);
        if (manual != null) {
            return single(manual);
        }

        return mergePublic(sources);
    }

    /**
     * 한 행이 통째로 이기는 경우입니다.
     *
     * 충돌이 없습니다.
     * 비교할 상대가 없기 때문이며, 공공 소스가 다른 말을 하고 있어도 세지 않습니다.
     * 사람이 이미 확인해 값을 정한 자리라 "출처에 따라 조건이 다르니 확인하세요" 가
     * 뜰 이유가 없고, 뜨면 관리자가 고칠수록 배지가 안 사라집니다.
     *
     * 공공 값과의 차이는 다른 데서 봅니다.
     * 정정 이력이 before 와 after 를 담고 있고, 원재료 표에 공공 행이 그대로 남습니다.
     */
    private static MergeResult single(PetPolicySource source) {
        return new MergeResult(source.getFields(), false, source.getSource(), List.of(),
                allFieldsFrom(source.getSource()));
    }

    /**
     * 스무 칸 전부를 한 소스가 이긴 것으로 적습니다.
     *
     * 값이 비어 있는 칸도 적습니다.
     * 정정 행의 빈 칸은 "안 건드린 칸" 이 아니라 "정보 없음으로 판단한 칸" 이라
     * 그 판단의 주인도 그 행이기 때문입니다.
     * 이렇게 적어 두어야 batch 가 그 칸의 공공 근거를 내보내지 않습니다.
     */
    private static Map<String, List<SourceType>> allFieldsFrom(SourceType source) {
        Map<String, List<SourceType>> fieldSources = new LinkedHashMap<>();
        for (FieldSpec<?> spec : FieldSpec.ALL) {
            fieldSources.put(spec.name(), List.of(source));
        }
        return fieldSources;
    }

    /**
     * 공공 3종을 필드 단위로 합칩니다.
     *
     * 칸마다 순위가 앞선 소스의 값을 취하되, 뒤선 소스가 다른 값을 말했으면
     * 그 사실을 충돌로 남깁니다. 값을 고르는 것과 갈렸는지 보는 것이 같은 비교라
     * 한 번의 순회에서 둘 다 나옵니다.
     */
    private static MergeResult mergePublic(List<PetPolicySource> sources) {
        List<PetPolicySource> ordered = orderByPriority(sources);
        if (ordered.isEmpty()) {
            return MergeResult.empty();
        }

        PolicyFields.PolicyFieldsBuilder builder = PolicyFields.builder();
        List<MergeResult.FieldConflict> conflicts = new ArrayList<>();
        Map<String, List<SourceType>> fieldSources = new LinkedHashMap<>();

        for (FieldSpec<?> spec : FieldSpec.ALL) {
            mergeField(spec, ordered, builder, conflicts, fieldSources);
        }

        return new MergeResult(
                builder.build(),
                !conflicts.isEmpty(),
                ordered.getFirst().getSource(),
                conflicts,
                fieldSources
        );
    }

    /**
     * 한 칸을 합칩니다.
     *
     * 와일드카드를 실제 타입으로 잡기 위한 자리이기도 합니다.
     * FieldSpec 목록이 여러 타입을 섞어 담고 있어 순회만으로는 getter 와 setter 의
     * 타입이 같다는 것을 컴파일러가 모릅니다.
     */
    private static <T> void mergeField(FieldSpec<T> spec,
                                       List<PetPolicySource> ordered,
                                       PolicyFields.PolicyFieldsBuilder builder,
                                       List<MergeResult.FieldConflict> conflicts,
                                       Map<String, List<SourceType>> fieldSources) {
        Map<SourceType, T> stated = new LinkedHashMap<>();
        for (PetPolicySource source : ordered) {
            T value = spec.getter().apply(source.getFields());
            if (isStated(value)) {
                stated.put(source.getSource(), value);
            }
        }

        if (stated.isEmpty()) {
            return;
        }

        if (spec.list()) {
            mergeListField(spec, stated, builder, conflicts, fieldSources);
            return;
        }

        // 순서가 우선순위 순이라 첫 값이 이김
        Map.Entry<SourceType, T> chosen = stated.entrySet().iterator().next();
        spec.setter().accept(builder, chosen.getValue());
        fieldSources.put(spec.name(), List.of(chosen.getKey()));

        if (hasDifferentValue(spec, stated.values())) {
            conflicts.add(new MergeResult.FieldConflict(spec.name(), toValueMap(stated)));
        }
    }

    /**
     * 소스들이 말한 값 중에 서로 다른 것이 있는지 봅니다.
     *
     * distinct 를 쓰지 않는 것은 equals 로 안 되는 칸이 있기 때문입니다.
     * BigDecimal 은 10 과 10.00 을 다르게 보는데 소스마다 표기가 갈립니다.
     * 칸마다 같음의 뜻이 다를 수 있어 그 판단을 FieldSpec 이 가집니다.
     */
    private static <T> boolean hasDifferentValue(FieldSpec<T> spec, Iterable<T> values) {
        T first = null;
        for (T value : values) {
            if (first == null) {
                first = value;
                continue;
            }
            if (!spec.sameAs().test(first, value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 목록형 칸을 합칩니다.
     *
     * 한쪽이 다른 쪽을 담고 있으면 어긋난 것이 아니라 한쪽이 더 자세한 것입니다.
     * 구역 이름은 "여기는 안 된다" 를 나열하는 것이라, 한 소스가 둘을 적고
     * 다른 소스가 하나만 적었다고 해서 그 하나만 안 되는 것이 아닙니다.
     * 그래서 합집합을 취해 더 자세한 쪽을 씁니다.
     *
     * 서로 겹치지 않는 원소가 양쪽에 있을 때만 충돌입니다.
     * 그때는 실제로 다른 구역을 지목한 것입니다.
     *
     * <b>승자는 합집합을 쌓는 이 순회에서 함께 정합니다.</b>
     * 우선순위 순으로 원소를 더해 가며 새 원소를 하나라도 보탠 소스만 승자입니다.
     * 값 칸이 "처음 말한 소스만" 인 것과 같은 원리입니다.
     * <pre>
     * 공사 ["실내", "잔디"] · 고캠핑 ["실내"]   승자 공사      고캠핑은 새로 보탠 것이 없음
     * 공사 [] · 고캠핑 ["실내"]               승자 고캠핑    빈 목록은 "해당 없음" 이라 보탠 것이 아님
     * 공사 [] · 고캠핑 []                    승자 둘 다     최종 값인 빈 목록을 함께 만듦
     * </pre>
     * 보탠 것이 없는 소스를 승자로 두면 batch 가 그 소스의 근거를 내보냅니다.
     * 빈 목록을 말한 소스라면 최종 값 ["실내"] 옆에 "제한 구역 없음" 이 붙어 반대 말을 하게 됩니다.
     */
    @SuppressWarnings("unchecked")
    private static <T> void mergeListField(FieldSpec<T> spec,
                                           Map<SourceType, T> stated,
                                           PolicyFields.PolicyFieldsBuilder builder,
                                           List<MergeResult.FieldConflict> conflicts,
                                           Map<String, List<SourceType>> fieldSources) {
        List<List<String>> lists = stated.values().stream()
                .map(value -> (List<String>) value)
                .toList();

        // 순서를 지켜 합침 — 관리자 화면이 이 순서로 펼쳐 보여줌
        // 합치면서 새 원소를 하나라도 더한 소스를 승자로 모음
        List<String> union = new ArrayList<>();
        List<SourceType> contributors = new ArrayList<>();
        for (Map.Entry<SourceType, T> entry : stated.entrySet()) {
            boolean added = false;
            for (String item : (List<String>) entry.getValue()) {
                if (!union.contains(item)) {
                    union.add(item);
                    added = true;
                }
            }
            if (added) {
                contributors.add(entry.getKey());
            }
        }
        spec.setter().accept(builder, (T) union);

        // 모두 빈 목록이면 보탠 소스가 없으나 최종 값을 함께 만든 것이라 전부 승자임
        fieldSources.put(spec.name(),
                union.isEmpty() ? List.copyOf(stated.keySet()) : contributors);

        if (hasDisjointPair(lists)) {
            conflicts.add(new MergeResult.FieldConflict(spec.name(), toValueMap(stated)));
        }
    }

    /**
     * 서로 상대를 담지 않는 쌍이 있는지 봅니다.
     *
     * 포함 관계이기만 하면 어느 쪽이 더 자세한 것이므로 어긋남이 아닙니다.
     * 한 쌍이라도 서로 없는 원소를 가지면 그 칸은 갈린 것입니다.
     */
    private static boolean hasDisjointPair(List<List<String>> lists) {
        for (int i = 0; i < lists.size(); i++) {
            for (int j = i + 1; j < lists.size(); j++) {
                List<String> left = lists.get(i);
                List<String> right = lists.get(j);
                if (!left.containsAll(right) && !right.containsAll(left)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 그 소스가 이 칸에 대해 무언가 말했는지입니다.
     *
     * null 은 "정보 없음" 이라 말한 것이 아닙니다.
     * 빈 목록은 "해당 없음" 이라 말한 것이므로 값으로 셉니다.
     * 둘을 같게 다루면 "제한 구역이 없다" 는 사실이 사라집니다.
     */
    private static boolean isStated(Object value) {
        return value != null;
    }

    /**
     * 충돌 기록에 담을 형태로 바꿉니다.
     *
     * 값을 그대로 담습니다.
     * jsonb 로 직렬화될 때 문자열·숫자·불리언·목록이 각자 형태를 유지합니다.
     * 키는 소스 이름입니다. 우선순위 순서를 그대로 지킵니다.
     */
    private static <T> Map<String, Object> toValueMap(Map<SourceType, T> stated) {
        Map<String, Object> values = new LinkedHashMap<>();
        stated.forEach((source, value) -> values.put(source.name(), value));
        return values;
    }

    /**
     * 공공 소스만 남겨 우선순위 순으로 늘어놓습니다.
     *
     * 목록에 MANUAL 이나 OWNER 가 섞여 있으면 여기 오지 않습니다.
     * 그 경우는 앞에서 통째로 이기는 길로 빠집니다.
     */
    private static List<PetPolicySource> orderByPriority(List<PetPolicySource> sources) {
        List<PetPolicySource> ordered = new ArrayList<>();
        for (SourceType type : PUBLIC_PRIORITY) {
            PetPolicySource found = findBySource(sources, type);
            if (found != null) {
                ordered.add(found);
            }
        }
        return ordered;
    }

    private static PetPolicySource findBySource(List<PetPolicySource> sources, SourceType type) {
        for (PetPolicySource source : sources) {
            if (source != null && source.getSource() == type) {
                return source;
            }
        }
        return null;
    }
}
