package com.pawtrail.policy.domain.model;

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
 * 관리자가 조건을 고친 이력입니다.
 *
 * 이 표가 필요한 이유는 원재료 표가 장소당 소스별로 한 행이기 때문입니다.
 * 같은 장소를 두 번 정정하면 MANUAL 행을 덮어써 앞선 값도 사유도 사라집니다.
 * 원재료의 reason 만으로는 "왜" 만 남고 "무엇을 어떻게" 가 안 남아 문장이 공중에 뜹니다.
 *
 * BaseEntity 를 상속하지 않습니다.
 * 한 번 쓰고 고치지 않는 표라 updated 와 deleted 계열 넷이 죽은 컬럼이 되고,
 * 특히 deleted_at 이 있으면 이력을 지울 수 있는 것으로 읽혀
 * 이 표의 존재 이유가 무너집니다.
 * place 의 place_source_detach 를 같은 이유로 미상속으로 두었습니다.
 *
 * 재병합은 여기에 남기지 않습니다.
 * 값을 바꾸는 동작이 아니고, 결과가 실제로 달라지면 policy_version 이 올라
 * policy.changed 가 나가므로 그쪽에 이미 흔적이 남습니다.
 */
@Entity
@Table(name = "policy_correction_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyCorrectionLog {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    // 누가 알려준 값인지임
    // 업주가 알려준 것과 관리자 추정은 신뢰도가 달라 나중에 갈라 볼 이유가 있음
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 20)
    private SourceType source;

    // 고치기 전과 후의 조건 한 벌임
    //
    // 전후를 함께 담는 것은 이력을 두는 이유가 "원래 뭐였지" 이기 때문임
    // 뒤만 남기면 그 질문에 답을 못 함
    // policy 가 덮어쓰기 전에 기존 행을 어차피 읽으므로 조회가 더 붙지 않음
    //
    // 스무 필드 스냅샷이라 null 값이 반드시 섞임
    // PolicyFields 가 null 을 "정보 없음" 으로 쓰기 때문이며
    // Map.copyOf 는 null 값에서 NullPointerException 을 냄
    // JsonSnapshot 이 null 을 그대로 두면서 깊게 복사함
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value", nullable = false, updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> afterValue;

    @Column(name = "reason", nullable = false, updatable = false, columnDefinition = "text")
    private String reason;

    // 고친 관리자임
    //
    // BaseEntity 를 안 쓰므로 JPA Auditing 이 채우지 않음
    // AuditorProvider.current() 로 꺼내 서비스로 넘김
    //
    // * 배치는 여기에 쓰지 않음
    //   배치의 흔적은 pet_policy_source 의 추출 이력 넷에 따로 남음
    @Column(name = "corrected_by", nullable = false, updatable = false, length = 45)
    private String correctedBy;

    @Column(name = "corrected_at", nullable = false, updatable = false)
    private LocalDateTime correctedAt;

    private PolicyCorrectionLog(UUID placeId, SourceType source,
                                Map<String, Object> beforeValue, Map<String, Object> afterValue,
                                String reason, String correctedBy) {
        this.placeId = placeId;
        this.source = source;
        this.beforeValue = JsonSnapshot.deepCopy(beforeValue);
        this.afterValue = JsonSnapshot.deepCopy(afterValue);
        this.reason = reason;
        this.correctedBy = correctedBy;
        this.correctedAt = LocalDateTime.now();
    }

    /**
     * 정정 이력을 남깁니다.
     *
     * 조건을 덮어쓰는 것과 한 트랜잭션 안에서 이뤄져야 합니다.
     * 조건만 바뀌고 이 행이 안 남으면 앞선 값이 그대로 사라집니다.
     *
     * 고치는 메서드가 없습니다.
     * 이력을 나중에 손대는 길을 두지 않는 것이 이 표의 뜻입니다.
     */
    public static PolicyCorrectionLog of(UUID placeId, SourceType source,
                                         Map<String, Object> beforeValue,
                                         Map<String, Object> afterValue,
                                         String reason, String correctedBy) {
        if (placeId == null || source == null) {
            throw new IllegalArgumentException("placeId 와 source 는 필수입니다.");
        }
        if (source != SourceType.MANUAL && source != SourceType.OWNER) {
            throw new IllegalArgumentException("정정의 소스는 MANUAL 이거나 OWNER 여야 합니다.");
        }
        if (beforeValue == null || afterValue == null) {
            throw new IllegalArgumentException("정정 전후 값은 필수입니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("정정에는 사유가 필요합니다.");
        }
        if (correctedBy == null || correctedBy.isBlank()) {
            throw new IllegalArgumentException("정정한 관리자는 필수입니다.");
        }
        return new PolicyCorrectionLog(placeId, source, beforeValue, afterValue,
                reason, correctedBy);
    }
}
