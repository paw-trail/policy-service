package com.pawtrail.policy.application.dto.output;

import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicySource;
import java.time.LocalDateTime;

/**
 * 지금 병합에서 이기고 있는 정정 행입니다.
 *
 * 관리자 화면이 출처 드롭다운과 사유를 다시 보여 주는 데 씁니다.
 * OWNER 가 이기고 있으면 화면이 출처를 미리 OWNER 로 골라 둘 수 있습니다.
 * MANUAL 로 저장하면 가려져 409 가 나는 자리이기 때문입니다.
 *
 * @param source      MANUAL 또는 OWNER
 * @param reason      마지막으로 저장한 사유. 관리자 내부 메모
 * @param correctedAt 마지막으로 저장한 시각
 */
public record CorrectionOutput(
        SourceType source,
        String reason,
        LocalDateTime correctedAt
) {

    public static CorrectionOutput from(PetPolicySource correction) {
        return new CorrectionOutput(
                correction.getSource(),
                correction.getReason(),
                correction.getExtractedAt()
        );
    }
}
