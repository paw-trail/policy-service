package com.pawtrail.policy.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import com.pawtrail.policy.domain.enums.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    // 소스끼리 값이 갈린 필드가 하나라도 있는지임
    //
    // 상세 내역은 policy_conflict 가 갖고 이 값은 배지 표시용임
    // 장소 상세가 이 값이 참일 때만 충돌 조회를 부르므로 헛 호출이 안 나감
    @Column(name = "has_conflict", nullable = false)
    private boolean hasConflict;

    // 이 병합에서 최상위로 이긴 티어임
    //
    // * 필드별 출처가 아님
    //   그것은 policy_evidence 가 (place_id, source, field_name) 으로 갖고 있고
    //   화면의 "출처: 고캠핑" 한 줄도 그쪽에서 나옴
    // 이 값은 사람이 손댄 조건인지를 관리자 목록에서 한눈에 거르는 용도임
    @Enumerated(EnumType.STRING)
    @Column(name = "source_priority", length = 20)
    private SourceType sourcePriority;

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
                      SourceType sourcePriority) {
        this.placeId = placeId;
        this.fields = fields;
        this.hasConflict = hasConflict;
        this.sourcePriority = sourcePriority;
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
                                   boolean hasConflict, SourceType sourcePriority) {
        if (placeId == null) {
            throw new IllegalArgumentException("placeId 는 필수입니다.");
        }
        if (fields == null) {
            throw new IllegalArgumentException("조건은 필수입니다.");
        }
        return new PetPolicy(placeId, fields, hasConflict, sourcePriority);
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
     * @param changed 병합 결과가 이전과 달라졌는지
     */
    public void remerge(PolicyFields fields, boolean hasConflict,
                        SourceType sourcePriority, boolean changed) {
        if (fields == null) {
            throw new IllegalArgumentException("조건은 필수입니다.");
        }
        this.fields = fields;
        this.hasConflict = hasConflict;
        this.sourcePriority = sourcePriority;
        this.mergedAt = LocalDateTime.now();
        if (changed) {
            this.policyVersion++;
        }
    }
}
