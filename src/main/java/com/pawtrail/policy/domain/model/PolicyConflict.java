package com.pawtrail.policy.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import com.pawtrail.policy.domain.enums.ConflictType;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.support.JsonSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * 소스끼리 또는 한 소스 안에서 값이 어긋난 자리입니다.
 *
 * 어긋났다고 해서 한쪽을 버리지 않습니다.
 * 우선순위대로 병합하되 어긋났다는 사실을 남겨 관리자가 볼 수 있게 합니다.
 * 어느 쪽이 맞는지는 우리가 판단할 수 없고, 버리면 그 판단의 근거도 사라집니다.
 *
 * 사용자에게는 배지로만 알리고 문구가 원인을 단정하지 않게 씁니다.
 * 소스가 틀린 것인지 장소가 바뀐 것인지 우리는 모릅니다.
 */
@Entity
@Table(name = "policy_conflict")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyConflict extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    @Column(name = "field_name", nullable = false, updatable = false, length = 40)
    private String fieldName;

    // 어느 소스의 것인지임
    //
    // INTRA_SOURCE 면 그 소스이고 CROSS_SOURCE 는 null 임
    // 소스 여럿에 걸친 어긋남이라 한 값으로 담을 수 없음
    //
    // 이 칸이 필요한 이유는 재추출 때문임
    // extract 가 소스별로 보내므로 그 소스의 것만 갈아 끼워야 하는데
    // 담을 칸이 없으면 장소 전체를 지우게 되어
    // 이번 요청에 없는 다른 소스의 기록까지 사라짐
    @Enumerated(EnumType.STRING)
    @Column(name = "source", updatable = false, length = 20)
    private SourceType source;

    // 소스별로 무엇이라고 했는지임
    //
    // 필드마다 타입이 달라(boolean · numeric · text[]) 컬럼으로는 못 담음
    // 관리자 화면이 그대로 펼쳐 보여주기만 하고 조건으로 조회하지 않으므로
    // jsonb 로 두어도 인덱스가 필요하지 않음
    //
    // 값에 목록이 섞임 — excluded_zones 처럼 text[] 인 필드가 넷임
    // 바깥 맵만 복사하면 그 목록이 원본과 같은 것을 가리켜
    // 감지 시점의 값이 나중에 바뀔 수 있으므로 JsonSnapshot 으로 깊게 복사함
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_values", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> sourceValues;

    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_type", nullable = false, updatable = false, length = 16)
    private ConflictType conflictType;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime detectedAt;

    // 관리자가 정정했는지임
    //
    // place_pending_update 의 status 와 달리 개명하지 않았음
    // 저쪽은 값이 셋이라 이름이 boolean 처럼 읽히는 것이 문제였고
    // 여기는 실제로 참 거짓 둘뿐임
    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    private PolicyConflict(UUID placeId, String fieldName, Map<String, Object> sourceValues,
                           ConflictType conflictType, SourceType source) {
        this.placeId = placeId;
        this.fieldName = fieldName;
        this.sourceValues = JsonSnapshot.deepCopy(sourceValues);
        this.conflictType = conflictType;
        this.source = source;
        this.detectedAt = LocalDateTime.now();
        this.resolved = false;
    }

    /**
     * 소스끼리 갈린 자리를 남깁니다.
     *
     * policy 가 병합하면서 스스로 찾는 종류입니다.
     * 같은 장소의 원재료 행들을 나란히 놓고 필드별로 비교합니다.
     *
     * 소스가 둘 이상이어야 성립합니다.
     * 하나뿐인데 이 종류로 남기면 무엇과 무엇이 갈렸는지 말할 수 없습니다.
     */
    public static PolicyConflict crossSource(UUID placeId, String fieldName,
                                             Map<String, Object> sourceValues) {
        validate(placeId, fieldName, sourceValues);
        if (sourceValues.size() < 2) {
            throw new IllegalArgumentException("소스 간 충돌은 값이 둘 이상이어야 합니다.");
        }
        return new PolicyConflict(placeId, fieldName, sourceValues,
                ConflictType.CROSS_SOURCE, null);
    }

    /**
     * 한 소스 안에서 갈린 자리를 남깁니다.
     *
     * extract 가 찾아 실어 보내는 종류입니다.
     * 필드값과 본문이 서로 다른 말을 하는 경우이며 고캠핑에서 28건 확인됐습니다.
     * policy 는 한 소스의 조건 한 벌만 받으므로 원본의 자기모순을 알 방법이 없습니다.
     */
    public static PolicyConflict intraSource(UUID placeId, SourceType source, String fieldName,
                                             Map<String, Object> sourceValues) {
        validate(placeId, fieldName, sourceValues);
        if (source == null) {
            throw new IllegalArgumentException("소스 내 충돌은 어느 소스인지가 필요합니다.");
        }
        return new PolicyConflict(placeId, fieldName, sourceValues,
                ConflictType.INTRA_SOURCE, source);
    }

    /**
     * 관리자가 정정해 이 충돌이 닫혔음을 표시합니다.
     *
     * 행을 지우지 않습니다.
     * 어긋났던 사실 자체가 기록이고, 지우면 왜 이 값이 됐는지를 되짚을 수 없습니다.
     */
    public void resolve() {
        this.resolved = true;
    }

    private static void validate(UUID placeId, String fieldName,
                                 Map<String, Object> sourceValues) {
        if (placeId == null) {
            throw new IllegalArgumentException("placeId 는 필수입니다.");
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName 은 필수입니다.");
        }
        if (sourceValues == null || sourceValues.isEmpty()) {
            throw new IllegalArgumentException("어긋난 값이 비어 있을 수 없습니다.");
        }
    }
}
