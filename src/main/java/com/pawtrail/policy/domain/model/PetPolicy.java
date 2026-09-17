package com.pawtrail.policy.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.support.JsonSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 소스들을 합쳐 만든 이 장소의 최종 조건입니다.
 *
 * verdict 가 판정할 때 읽어 가는 대상이며 장소당 한 행입니다.
 *
 * PK 가 place_id 입니다.
 * 별도 식별자를 두면 "한 장소에 조건이 둘일 수 있다" 로 읽히는데 그런 상태는 없습니다.
 * 따라서 @UuidGenerator 를 쓰지 않고 병합할 때 받은 값을 그대로 넣습니다.
 *
 * 병합은 세 단계입니다.
 *   OWNER 행이 있으면            그 행이 통째로 이 표가 됨
 *   없고 MANUAL 행이 있으면       그 행이 통째로 됨
 *   둘 다 없으면                 공공 3종을 필드 단위로 합침
 *
 * 공공 3종을 필드 단위로 합치는 이유는 소스마다 채우는 칸이 다르기 때문입니다.
 * 한 소스를 통째로 쓰면 나머지가 채웠을 칸이 전부 null 이 되고,
 * null 은 "정보 없음" 이라 판정이 UNKNOWN 으로 떨어집니다.
 * 가진 근거를 버려서 모른다고 답하는 셈입니다.
 */
@Entity
@Table(name = "pet_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetPolicy extends BaseEntity {

    // 장소 식별자가 곧 PK 임
    @Id
    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    @Embedded
    private PolicyFields fields;

    // 열린 충돌이 하나라도 있는지임
    //
    // 병합이 찾은 소스 간 어긋남이 있거나 병합에 참여한 소스의 소스 내 어긋남이 있으면 참임
    // 정정 행이 이기면 참여한 소스가 그 행 하나라 공공 소스의 어긋남은 세지 않음
    // 상세 내역은 policy_conflict 가 갖고 이 값은 배지 표시용임
    // 장소 상세가 이 값이 참일 때만 충돌 조회를 부르므로 헛 호출이 안 나감
    // * 공개 충돌 목록은 이 값이 센 것과 같은 집합이어야 함 — 배지와 목록이 어긋나면 안 됨
    @Column(name = "has_conflict", nullable = false)
    private boolean hasConflict;

    // 이 병합에서 최상위로 이긴 티어임
    //
    // * 필드별 출처가 아님 — 그것은 아래 fieldSources 가 가짐
    //   V20 주석은 policy_evidence 가 필드별 출처를 준다고 적었으나
    //   근거 표에는 병합에서 진 소스의 근거도 들어 있어 성립하지 않음 (V22 에서 바로잡음)
    // 이 값은 사람이 손댄 조건인지를 관리자 목록에서 한눈에 거르는 용도임
    @Enumerated(EnumType.STRING)
    @Column(name = "source_priority", length = 20)
    private SourceType sourcePriority;

    // 칸마다 그 값을 만든 소스임
    //
    // 키는 조건 이름(FieldSpec 이름) · 값은 소스 이름 목록이며 우선순위 순서임
    //   {"scope": ["PET_TOUR"], "excludedZones": ["PET_TOUR", "CULTURE_CSV"]}
    // 아무 소스도 말하지 않은 칸은 키가 없음
    //
    // * batch 가 근거를 고를 때 씀
    //   근거 표는 소스마다 제 근거를 가져 병합에서 진 소스의 것도 남아 있음
    //   칸 이름으로만 고르면 정정한 값 옆에 옛 공공 문구가 출처로 뜸
    // * 값을 고르는 순회에서 함께 나오므로 값과 어긋날 수 없음
    //   재병합이 매번 덮어써 늘 최신임
    // * 판을 올리는 기준에는 넣지 않음
    //   승자만 바뀐 재병합은 사용자에게 보이는 값이 같아 알림 대상이 아님
    // * 값을 Map<String, Object> 로 두는 것은 policy_conflict.source_values 와 같은 길을 가려는 것임
    //   바깥에는 sourcesOf 로 소스 열거값을 돌려줌
    @Getter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_sources", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> fieldSources;

    @Column(name = "merged_at", nullable = false)
    private LocalDateTime mergedAt;

    // 갱신될 때마다 오름
    //
    // policy.changed 발행 기준이며 받는 쪽이 자기가 아는 판과 비교함
    // 재병합해도 결과가 같으면 오르지 않음
    // 그래야 값이 안 바뀐 재병합으로 알림이 나가지 않음
    @Column(name = "policy_version", nullable = false)
    private int policyVersion;

    private PetPolicy(UUID placeId, PolicyFields fields, boolean hasConflict,
                      SourceType sourcePriority, Map<String, List<SourceType>> fieldSources) {
        this.placeId = placeId;
        this.fields = fields;
        this.hasConflict = hasConflict;
        this.sourcePriority = sourcePriority;
        this.fieldSources = toSnapshot(fieldSources);
        this.mergedAt = LocalDateTime.now();
        this.policyVersion = 1;
    }

    /**
     * 이 장소의 조건을 처음 만듭니다.
     *
     * 판이 1 부터 시작합니다.
     * 0 이 아닌 것은 받는 쪽이 "아직 아무것도 모름" 과 "첫 판" 을 가릴 수 있게 하기 위해서입니다.
     */
    public static PetPolicy merged(UUID placeId, PolicyFields fields,
                                   boolean hasConflict, SourceType sourcePriority,
                                   Map<String, List<SourceType>> fieldSources) {
        if (placeId == null) {
            throw new IllegalArgumentException("placeId 는 필수입니다.");
        }
        if (fields == null) {
            throw new IllegalArgumentException("조건은 필수입니다.");
        }
        return new PetPolicy(placeId, fields, hasConflict, sourcePriority, fieldSources);
    }

    /**
     * 다시 병합한 결과로 갈아 끼웁니다.
     *
     * 값이 실제로 달라졌을 때만 판이 오릅니다.
     * 재병합은 소스가 안 바뀌어도 부를 수 있는 동작이라, 부를 때마다 판을 올리면
     * 아무것도 안 바뀐 재병합으로 policy.changed 가 나가고
     * 즐겨찾기한 사람 전부에게 알림이 갑니다.
     *
     * 값이 같은지는 호출부가 판단해 넘깁니다.
     * 무엇이 달라졌는지(changedFields)를 어차피 계산해야 하고,
     * 그 계산을 여기서 또 하면 같은 비교가 두 번 돌기 때문입니다.
     *
     * 칸별 승자는 판과 상관없이 늘 덮어씁니다.
     * 값이 같아도 승자가 바뀔 수 있고, 그때 보여 줄 근거도 바뀌어야 하기 때문입니다.
     *
     * @param changed 병합 결과가 이전과 달라졌는지
     */
    public void remerge(PolicyFields fields, boolean hasConflict,
                        SourceType sourcePriority, Map<String, List<SourceType>> fieldSources,
                        boolean changed) {
        if (fields == null) {
            throw new IllegalArgumentException("조건은 필수입니다.");
        }
        this.fields = fields;
        this.hasConflict = hasConflict;
        this.sourcePriority = sourcePriority;
        this.fieldSources = toSnapshot(fieldSources);
        this.mergedAt = LocalDateTime.now();
        if (changed) {
            this.policyVersion++;
        }
    }

    /**
     * 조건 스무 가지를 돌려줍니다.
     *
     * <b>null 을 돌려주지 않습니다.</b>
     * 하이버네이트는 임베디드의 컬럼이 전부 NULL 이면 값 객체 자체를 null 로 읽습니다.
     * 스무 칸이 모두 비어 있는 병합 결과가 그렇게 되는데, 다시 읽은 뒤 재병합하면
     * 이전 값과 비교하다 멈추고 batch 는 그 행을 담으려다 멈춥니다.
     * 한 트랜잭션 안에서는 저장한 객체가 그대로 돌아와 드러나지 않습니다.
     *
     * 비어 있는 조건은 "정보 없음" 이라는 값이므로 빈 값 객체로 돌려줍니다.
     * batch 가 이 행을 빼지 않고 담기로 한 것도 그 뜻을 살리려는 것입니다.
     */
    public PolicyFields getFields() {
        return fields == null ? PolicyFields.empty() : fields;
    }

    /**
     * 그 칸의 값을 만든 소스들입니다.
     *
     * 아무 소스도 말하지 않은 칸이면 빈 목록입니다.
     * V22 이전에 만들어져 아직 다시 병합되지 않은 행도 빈 목록이며,
     * 그 경우 batch 는 그 칸의 근거를 내보내지 않습니다. 틀린 근거가 아니라 빠진 근거가 됩니다.
     */
    public List<SourceType> sourcesOf(String fieldName) {
        Object value = fieldSources == null ? null : fieldSources.get(fieldName);
        if (!(value instanceof List<?> names)) {
            return List.of();
        }
        List<SourceType> sources = new ArrayList<>(names.size());
        for (Object name : names) {
            sources.add(SourceType.valueOf(String.valueOf(name)));
        }
        return List.copyOf(sources);
    }

    /**
     * 승자 표를 jsonb 에 담을 형태로 바꿉니다.
     *
     * 소스는 이름 문자열로 담습니다.
     * 열거값을 그대로 넣으면 읽어 올 때 문자열로 돌아와 같은 칸의 타입이 갈립니다.
     */
    private static Map<String, Object> toSnapshot(Map<String, List<SourceType>> fieldSources) {
        Map<String, Object> names = new LinkedHashMap<>();
        if (fieldSources != null) {
            fieldSources.forEach((field, sources) ->
                    names.put(field, sources.stream().map(SourceType::name).toList()));
        }
        return JsonSnapshot.deepCopy(names);
    }
}
