package com.pawtrail.policy.domain.enums;

/**
 * 추출 진행 상태입니다.
 *
 * 배치가 중단되면 PENDING 부터 이어서 실행합니다.
 * 전량을 다시 도는 것은 LLM 비용과 시간이 그대로 두 배가 됩니다.
 */
public enum ExtractionStatus {

    // 아직 뽑지 않았음
    PENDING,

    // 뽑았음
    DONE,

    // 뽑다 실패했음
    //
    // 재시도 대상임
    // 실패 원인은 LLM 응답이 스키마를 어긴 경우가 대부분이며,
    // 그 판별은 extract 가 함
    FAILED
}
