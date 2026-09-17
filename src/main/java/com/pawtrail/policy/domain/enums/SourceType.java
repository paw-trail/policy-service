package com.pawtrail.policy.domain.enums;

/**
 * 조건을 어디서 얻었는지입니다.
 *
 * 병합 우선순위가 이 순서를 따릅니다.
 * OWNER 가 가장 높고 MANUAL 이 그다음이며 공공 3종이 가장 낮습니다.
 * 관리자 정정을 별도 표가 아니라 소스 티어로 넣었기 때문에,
 * 배치가 새 값을 뽑아도 병합에서 자동으로 밀립니다.
 *
 * place 의 SourceType 과 값이 다릅니다.
 * 이름이 같다고 같은 열거형으로 보지 않도록 주의합니다.
 */
public enum SourceType {

    // 한국관광공사 반려동물 동반여행
    //
    // 셋 중 동반 조건을 본업으로 가진 유일한 소스임
    // 조건이 정형 필드로 들어 있어 공공 3종 안에서 가장 높음
    PET_TOUR,

    // 한국관광공사 고캠핑
    // 야영장 정보라 조건이 부대 정보에 섞여 있으나 size_rule 이 447건 있음
    GOCAMPING,

    // 문화정보원
    CULTURE_CSV,

    // 관리자가 고친 값
    //
    // 무엇을 왜 고쳤는지는 pet_policy_source.reason 과
    // policy_correction_log 에 남음
    MANUAL,

    // 장소가 직접 알려준 값
    //
    // MANUAL 과 나눠 둔 것은 신뢰도가 다르기 때문임
    // 업주가 알려준 것과 관리자 추정을 나중에 갈라 볼 이유가 실제로 있음
    OWNER;

    // MOIS_VET 은 없음
    // 행정안전부 동물병원 인허가 데이터에는 동반 조건이 없어
    // 이 표에 행이 생기지 않음

    /**
     * 사람이 넣은 정정 행의 출처인지입니다.
     *
     * 병합에서 통째로 이기는 티어이며, 이 판별이 여러 자리(원재료 행 · batch 출력 · 충돌 조회)에서
     * 같아야 해 한곳에 둡니다.
     */
    public boolean isCorrection() {
        return this == MANUAL || this == OWNER;
    }
}
