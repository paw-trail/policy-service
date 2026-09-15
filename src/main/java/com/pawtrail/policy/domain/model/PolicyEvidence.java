package com.pawtrail.policy.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import com.pawtrail.policy.domain.enums.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 어느 조건이 원문의 어느 문장에서 나왔는지입니다.
 *
 * 근거를 제시하는 것이 이 서비스의 정체성이고 그 실체가 이 표입니다.
 * 화면 세 자리가 전부 여기서 나옵니다.
 * 검색 카드의 한 줄 근거, 장소 상세 확인 사항의 항목별 이유와 출처,
 * 그리고 근거 원문 보기입니다.
 *
 * 발췌만 보여주면 LLM 이 고른 것을 LLM 근거로 확인하는 순환이 됩니다.
 * 그래서 원문 전체는 ingest 의 raw_document 가 따로 내보냅니다.
 */
@Entity
@Table(name = "policy_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyEvidence extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    // 이 근거가 어느 소스의 원문에서 나왔는지임
    // OWNER 면 상세에 "장소에서 직접 알려준 정보" 배지를 붙일 수 있음
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 20)
    private SourceType source;

    // 어느 조건의 근거인지임
    // pet_policy 의 컬럼 이름이 들어감
    @Column(name = "field_name", nullable = false, updatable = false, length = 40)
    private String fieldName;

    // 원문의 어느 필드에서 뽑았는지임
    // acmpyPsblCpam · etcAcmpyInfo · intro 처럼 소스가 쓰는 이름 그대로임
    @Column(name = "origin_field", nullable = false, updatable = false, length = 40)
    private String originField;

    // 그 필드 안에서 몇 번째 조각인지임
    //
    // etcAcmpyInfo 는 개행으로 쪼갬
    // 하이픈으로 쪼개는 방식은 본문에 하이픈이 섞여 있어 버렸음
    // 쪼갤 것이 없는 필드면 null 임
    @Column(name = "segment_index")
    private Integer segmentIndex;

    // 근거 문구 자체임
    // 사람이 읽고 왜 그런 판정인지 알 수 있어야 함
    @Column(name = "segment_text", nullable = false, columnDefinition = "text")
    private String segmentText;

    private PolicyEvidence(UUID placeId, SourceType source, String fieldName,
                           String originField, Integer segmentIndex, String segmentText) {
        this.placeId = placeId;
        this.source = source;
        this.fieldName = fieldName;
        this.originField = originField;
        this.segmentIndex = segmentIndex;
        this.segmentText = segmentText;
    }

    /**
     * 근거 한 줄을 남깁니다.
     *
     * 고치는 메서드를 두지 않았습니다.
     * 근거는 원문에서 뽑은 사실이라 고칠 일이 없고,
     * 재추출로 내용이 달라지면 옛 행을 무효화하고 새로 남깁니다.
     */
    public static PolicyEvidence of(UUID placeId, SourceType source, String fieldName,
                                    String originField, Integer segmentIndex, String segmentText) {
        if (placeId == null || source == null) {
            throw new IllegalArgumentException("placeId 와 source 는 필수입니다.");
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName 은 필수입니다.");
        }
        if (originField == null || originField.isBlank()) {
            throw new IllegalArgumentException("originField 는 필수입니다.");
        }
        if (segmentText == null || segmentText.isBlank()) {
            throw new IllegalArgumentException("근거 문구는 비어 있을 수 없습니다.");
        }
        return new PolicyEvidence(placeId, source, fieldName,
                originField, segmentIndex, segmentText);
    }
}
