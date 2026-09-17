package com.pawtrail.policy.presentation.request;

import com.pawtrail.policy.domain.enums.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 관리자가 조건을 정정하는 요청입니다.
 *
 * <b>전체 교체입니다.</b>
 * 스무 칸을 전부 보내고, 비어 있는 칸은 "정보 없음으로 판단한 칸" 입니다.
 * 부분 갱신으로 두면 null 이 "안 건드림" 과 "정보 없음" 두 뜻이 되는데 폼은 그 둘을 가를 수 없습니다.
 * 폼은 GET /api/v1/admin/policies/{placeId} 로 현재 값을 채운 뒤 고쳐 보냅니다.
 *
 * 출처가 MANUAL · OWNER 가 아니면 400 입니다. 서비스가 공통 에러 코드로 막습니다.
 *
 * @param source 정정의 출처. MANUAL(관리자 확인) 이거나 OWNER(장소가 직접 알려준 정보)
 * @param reason 왜 고쳤는지. 관리자 내부 메모라 사용자에게 나가지 않음
 * @param fields 정정한 조건 스무 칸
 */
public record ManualCorrectionRequest(

        @NotNull(message = "정정의 출처는 필수입니다.")
        SourceType source,

        @NotBlank(message = "정정 사유는 필수입니다.")
        String reason,

        @NotNull(message = "조건은 필수입니다.")
        @Valid
        PolicyFieldsRequest fields
) {
}
