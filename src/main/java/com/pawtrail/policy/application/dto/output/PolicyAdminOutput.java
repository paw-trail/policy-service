package com.pawtrail.policy.application.dto.output;

import java.util.UUID;

/**
 * 관리자 조건 정정 화면이 폼을 채우는 데 쓰는 현재 상태입니다.
 *
 * 정정은 전체 교체라 폼이 현재 값으로 채워져 있어야 합니다.
 * 빈 폼에서 한 칸만 고쳐 저장하면 나머지 칸이 전부 정보 없음으로 들어가 공공 소스가 채운 값이 지워집니다.
 *
 * 값은 사람이 읽는 문장이 아니라 원값입니다.
 * 폼의 예 / 아니오 / 정보 없음 버튼과 드롭다운을 원값으로 고르기 때문입니다.
 *
 * @param placeId    장소 식별자
 * @param fields     현재 병합 결과 스무 칸. 조건 행이 없으면 전부 null
 * @param correction 지금 이기고 있는 정정 행. 공공 병합이거나 행이 없으면 null
 */
public record PolicyAdminOutput(
        UUID placeId,
        PolicyFieldsOutput fields,
        CorrectionOutput correction
) {
}
