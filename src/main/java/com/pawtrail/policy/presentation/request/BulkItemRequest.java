package com.pawtrail.policy.presentation.request;

import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * 한 장소에 대해 한 소스가 말한 것 전부입니다.
 *
 * 같은 청크에 같은 장소가 여러 번 올 수 있습니다.
 * 소스가 다르면 다른 항목이며, 세 소스가 붙은 장소는 한 청크에 세 번 나올 수 있습니다.
 *
 * @param source           공공 3종만 옵니다. 정정은 관리자 API 로 들어오므로 여기로 오지 않습니다
 * @param extractionMethod 건마다 갈립니다. 한 레코드 안에서도 칸마다 달라 MIXED 라는 값이 있습니다
 */
public record BulkItemRequest(

        @NotNull(message = "장소 식별자는 필수입니다.")
        UUID placeId,

        @NotNull(message = "소스는 필수입니다.")
        SourceType source,

        @NotNull(message = "조건은 필수입니다.")
        @Valid
        PolicyFieldsRequest fields,

        @Valid
        List<EvidenceRequest> evidence,

        @Valid
        List<ConflictRequest> conflicts,

        @NotNull(message = "추출 방식은 필수입니다.")
        ExtractionMethod extractionMethod
) {

    /**
     * 목록 자리가 비어 있어도 빈 목록으로 다룹니다.
     *
     * 근거가 없는 조건이 실제로 있습니다.
     * 정형 필드를 규칙으로 파싱한 경우 인용할 문장이 따로 없기 때문입니다.
     */
    public List<EvidenceRequest> evidenceOrEmpty() {
        return evidence == null ? List.of() : evidence;
    }

    public List<ConflictRequest> conflictsOrEmpty() {
        return conflicts == null ? List.of() : conflicts;
    }
}
