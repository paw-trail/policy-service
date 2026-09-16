package com.pawtrail.policy.domain.model;

import com.pawtrail.common.entity.BaseEntity;
import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.ExtractionStatus;
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
import org.hibernate.annotations.UuidGenerator;

/**
 * 한 소스가 이 장소에 대해 말한 조건입니다.
 *
 * 병합하기 전의 원재료이며 장소당 소스별로 한 행입니다.
 * 이 표를 따로 두는 이유는 병합 규칙을 나중에 바꿀 수 있게 하기 위해서입니다.
 * 최종본만 두면 규칙이 바뀔 때마다 LLM 을 다시 돌려야 하는데,
 * 원재료가 남아 있으면 재병합만으로 끝납니다.
 *
 * MANUAL 과 OWNER 도 여기에 행으로 들어옵니다.
 * 관리자 정정을 별도 표가 아니라 소스 티어의 최상위로 넣었기 때문에,
 * 배치가 새 값을 뽑아도 병합에서 자동으로 밀립니다.
 *
 * uq_policy_source_place(place_id, source) 가 재추출의 멱등을 만듭니다.
 * 같은 장소를 다시 뽑으면 행이 쌓이는 것이 아니라 그 소스의 행을 갱신합니다.
 * 관리자가 같은 장소를 두 번 정정하면 MANUAL 행을 덮어써 앞선 값이 사라지는데,
 * 그것은 policy_correction_log 가 받습니다.
 *
 * BaseEntity 는 그대로 상속하되 deleted_at 과 deleted_by 는 항상 null 입니다.
 * 이 표에서 행이 사라지는 사건이 없습니다.
 * 재추출도 관리자 정정도 그 소스의 행을 갱신하지 지우지 않습니다.
 *
 * 소프트 딜리트를 쓰지 않기로 한 것은 uq_policy_source_place 때문이기도 합니다.
 * 무효화한 행을 남겨 두면 같은 소스를 다시 넣을 때 그 제약에 걸리는데,
 * 되살리는 경로가 없어 그 장소의 그 소스는 영영 막힙니다.
 * place 의 place_source_link 가 같은 이유로 같은 형태입니다.
 */
@Entity
@Table(name = "pet_policy_source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PetPolicySource extends BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // place_db 의 place.id 를 가리키나 외래 키를 걸지 않음
    // 서비스가 갈려 DB 가 다름
    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 20)
    private SourceType source;

    // 조건 스무 가지임
    // pet_policy 와 같은 값 객체를 씀
    @Embedded
    private PolicyFields fields;

    // ── 추출 이력 ────────────────────────────────────────────────────────
    // 아래 여섯은 pet_policy 에 없음
    // 병합 최종본은 "무엇이 결론인가" 만 담고 "어떻게 뽑았나" 는 이쪽에 남김

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_method", length = 10)
    private ExtractionMethod extractionMethod;

    // LLM 을 태웠다면 어느 모델인지임
    // 모델별 정확도를 나눠 재는 데 씀
    @Column(name = "extracted_by", length = 50)
    private String extractedBy;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 12)
    private ExtractionStatus status;

    // 왜 이 값인지임
    //
    // 관리자 정정에서 필수이고 수집 값에서는 비어 있음
    // 이 컬럼이 찼는지로 두 성격이 갈림
    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    private PetPolicySource(UUID placeId, SourceType source, PolicyFields fields,
                            ExtractionMethod extractionMethod, String extractedBy,
                            String promptVersion, LocalDateTime extractedAt,
                            ExtractionStatus status, String reason) {
        this.placeId = placeId;
        this.source = source;
        this.fields = fields;
        this.extractionMethod = extractionMethod;
        this.extractedBy = extractedBy;
        this.promptVersion = promptVersion;
        this.extractedAt = extractedAt;
        this.status = status;
        this.reason = reason;
    }

    /**
     * extract 가 뽑아 보낸 결과를 담습니다.
     *
     * 추출 이력이 함께 들어옵니다.
     * 어느 모델이 어느 프롬프트 판으로 언제 뽑았는지를 남겨야
     * 나중에 정확도를 모델별로 나눠 잴 수 있습니다.
     */
    public static PetPolicySource extracted(UUID placeId, SourceType source, PolicyFields fields,
                                            ExtractionMethod extractionMethod, String extractedBy,
                                            String promptVersion, LocalDateTime extractedAt) {
        validate(placeId, source, fields);
        return new PetPolicySource(placeId, source, fields,
                extractionMethod, extractedBy, promptVersion, extractedAt,
                ExtractionStatus.DONE, null);
    }

    /**
     * 관리자가 고친 값을 담습니다.
     *
     * 팩터리를 나눈 이유는 채우는 값이 다르기 때문입니다.
     * 사람이 넣은 행은 모델도 프롬프트 판도 없고 대신 사유가 필수입니다.
     * 하나로 두면 "LLM 이 뽑았는데 사유가 있는" 조합을 만들 수 있게 됩니다.
     *
     * source 는 MANUAL 이거나 OWNER 여야 합니다.
     * 공공 소스를 이 자리로 넣으면 배치가 다음에 그 행을 덮어써
     * 관리자가 고친 값이 조용히 사라집니다.
     */
    public static PetPolicySource corrected(UUID placeId, SourceType source,
                                            PolicyFields fields, String reason) {
        validate(placeId, source, fields);
        if (source != SourceType.MANUAL && source != SourceType.OWNER) {
            throw new IllegalArgumentException("정정의 소스는 MANUAL 이거나 OWNER 여야 합니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("정정에는 사유가 필요합니다.");
        }
        return new PetPolicySource(placeId, source, fields,
                ExtractionMethod.MANUAL, null, null, LocalDateTime.now(),
                ExtractionStatus.DONE, reason);
    }

    /**
     * 같은 소스를 다시 뽑았을 때 이 행을 갱신합니다.
     *
     * 새 행을 만들지 않는 것은 uq_policy_source_place 때문이기도 하지만,
     * 이 표의 뜻이 "지금 이 소스가 뭐라고 하는가" 이기 때문입니다.
     * 지난번에 뭐라고 했는지는 이 표의 답이 아닙니다.
     *
     * <b>정정으로 만들어진 행에는 쓸 수 없습니다.</b>
     * source 는 그대로 둔 채 추출 메타만 채우고 사유를 지우게 되는데,
     * 그러면 배치가 뽑은 값이 MANUAL 이나 OWNER 를 달고 병합 최상위를 차지합니다.
     * 정정을 소스 티어로 넣은 설계가 정확히 그 자리에서 뒤집힙니다.
     */
    public void replaceExtraction(PolicyFields fields, ExtractionMethod extractionMethod,
                                  String extractedBy, String promptVersion,
                                  LocalDateTime extractedAt) {
        if (fields == null) {
            throw new IllegalArgumentException("조건은 필수입니다.");
        }
        if (isCorrection()) {
            throw new IllegalStateException("정정으로 만들어진 소스에는 추출 결과를 덮을 수 없습니다.");
        }
        this.fields = fields;
        this.extractionMethod = extractionMethod;
        this.extractedBy = extractedBy;
        this.promptVersion = promptVersion;
        this.extractedAt = extractedAt;
        this.status = ExtractionStatus.DONE;
        this.reason = null;
    }

    /**
     * 관리자 정정으로 이 행을 갈아 끼웁니다.
     *
     * 전체 교체입니다.
     * 관리자 화면이 현재 병합값을 폼에 채워 보여주고 저장할 때 스무 값을 전부 보내므로,
     * 여기 들어오는 fields 는 관리자가 확인한 조건 한 벌입니다.
     * 따라서 null 도 "안 건드린 칸" 이 아니라 "정보 없음으로 판단한 칸" 입니다.
     */
    public void replaceCorrection(PolicyFields fields, String reason) {
        if (fields == null) {
            throw new IllegalArgumentException("조건은 필수입니다.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("정정에는 사유가 필요합니다.");
        }
        if (!isCorrection()) {
            throw new IllegalStateException("공공 소스의 행은 정정으로 덮을 수 없습니다.");
        }
        this.fields = fields;
        this.extractionMethod = ExtractionMethod.MANUAL;
        this.extractedBy = null;
        this.promptVersion = null;
        this.extractedAt = LocalDateTime.now();
        this.status = ExtractionStatus.DONE;
        this.reason = reason;
    }

    /**
     * 조건 스무 가지를 돌려줍니다.
     *
     * <b>null 을 돌려주지 않습니다.</b>
     * 하이버네이트는 임베디드의 컬럼이 전부 NULL 이면 값 객체 자체를 null 로 읽습니다.
     * extract 가 원문에서 조건을 하나도 못 찾아 스무 칸을 비워 보낸 행이 그렇게 되는데,
     * 같은 장소에 다른 소스가 들어와 재병합할 때 이 행을 다시 읽으면
     * 병합이 null 에서 칸을 꺼내다 멈춥니다.
     * 한 트랜잭션 안에서는 저장한 객체가 그대로 돌아와 드러나지 않습니다.
     *
     * 비어 있는 조건은 "정보 없음" 이라는 값이므로 빈 값 객체로 돌려줍니다.
     */
    public PolicyFields getFields() {
        return fields == null ? PolicyFields.empty() : fields;
    }

    /**
     * 사람이 넣은 행인지입니다.
     *
     * 갱신 경로가 둘로 갈리는 기준입니다.
     * 배치가 뽑은 행과 사람이 고친 행은 채우는 값이 반대라
     * 서로의 갱신 메서드가 먹으면 두 성격이 섞인 행이 만들어집니다.
     */
    public boolean isCorrection() {
        return source == SourceType.MANUAL || source == SourceType.OWNER;
    }

    private static void validate(UUID placeId, SourceType source, PolicyFields fields) {
        if (placeId == null || source == null) {
            throw new IllegalArgumentException("placeId 와 source 는 필수입니다.");
        }
        if (fields == null) {
            throw new IllegalArgumentException("조건은 필수입니다.");
        }
    }
}
