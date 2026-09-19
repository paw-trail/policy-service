package com.pawtrail.policy.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import com.pawtrail.policy.domain.enums.ExtractionMethod;
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
 *
 * 소스마다 제 근거를 가지므로 병합에서 진 소스의 근거도 남습니다.
 * 어느 근거를 보일지는 pet_policy 의 field_sources 로 가립니다.
 *
 * 근거마다 규칙이 읽었는지 모델이 읽었는지도 남깁니다.
 * 판정 화면이 이유마다 "공공데이터 항목" 과 "안내문을 AI 가 읽음" 을 가르는 재료입니다.
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
    // 조건 이름(FieldSpec 이름 · camelCase)이 들어감.  DB 컬럼 이름이 아님
    // bulk 요청이 그 밖의 이름을 400 으로 막음
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

    // 이 근거를 규칙이 읽었는지(RULE) 모델이 읽었는지(LLM)임
    //
    // 출처 행의 extraction_method 는 행 단위라 한 원문 안에서 칸마다 갈리면 MIXED 가 되어 칸을 못 가름
    // 원문 키로도 못 가름 — 규칙과 모델이 함께 읽는 키가 있음 (공사 동반 가능 동물 · 문화정보원 크기 · 요금)
    // 조각 번호로도 못 가름 — 모델 근거도 통째로 읽는 칸이면 비어 있음
    // * null 은 V25 이전에 들어와 아직 다시 뽑지 않은 근거임
    //   새 근거는 bulk 요청 검증이 RULE · LLM 둘만 받음
    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_method", updatable = false, length = 10)
    private ExtractionMethod extractionMethod;

    private PolicyEvidence(UUID placeId, SourceType source, String fieldName,
                           String originField, Integer segmentIndex, String segmentText,
                           ExtractionMethod extractionMethod) {
        this.placeId = placeId;
        this.source = source;
        this.fieldName = fieldName;
        this.originField = originField;
        this.segmentIndex = segmentIndex;
        this.segmentText = segmentText;
        this.extractionMethod = extractionMethod;
    }

    /**
     * 근거 한 줄을 남깁니다.
     *
     * 고치는 메서드를 두지 않았습니다.
     * 근거는 원문에서 뽑은 사실이라 고칠 일이 없고,
     * 재추출로 내용이 달라지면 그 소스의 근거를 통째로 지우고 새로 넣습니다.
     *
     * 추출 방식은 RULE · LLM 둘만 받습니다.
     * MIXED 는 출처 행의 값이라 근거 한 줄에는 뜻이 없고, MANUAL 정정은 근거 없이 들어옵니다.
     * 요청 검증이 먼저 막으므로 여기 걸리면 부르는 쪽 코드가 잘못된 것입니다.
     */
    public static PolicyEvidence of(UUID placeId, SourceType source, String fieldName,
                                    String originField, Integer segmentIndex, String segmentText,
                                    ExtractionMethod extractionMethod) {
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
        if (extractionMethod != ExtractionMethod.RULE && extractionMethod != ExtractionMethod.LLM) {
            throw new IllegalArgumentException("근거의 추출 방식은 RULE · LLM 만 됩니다.");
        }
        return new PolicyEvidence(placeId, source, fieldName,
                originField, segmentIndex, segmentText, extractionMethod);
    }
}
