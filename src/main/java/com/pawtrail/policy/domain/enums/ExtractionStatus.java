package com.pawtrail.policy.domain.enums;

/**
 * 추출 진행 상태입니다.
 *
 * 지금은 DONE 만 씁니다. 적재(bulk)로 들어오는 행은 이미 뽑은 결과라 언제나 DONE 이고,
 * 관리자 정정 행도 DONE 입니다.
 *
 * 어디까지 뽑았는지와 실패한 원문을 다시 뽑는 일은 ingest 의 원문 상태(PENDING · DONE · FAILED)가 맡습니다.
 * extract 가 처리한 원문을 그쪽에 적고, 실행이 멈추면 PENDING 으로 남은 원문부터 다시 가져갑니다.
 * 재개 지점을 두 곳에 두면 어긋날 자리만 생겨 이쪽은 쓰지 않기로 했습니다.
 *
 * PENDING · FAILED 는 그렇게 정하기 전에 둔 값이라 지금은 닿는 길이 없습니다.
 * 이미 쌓인 행과 명세가 이 이름을 알고 있어 값은 그대로 둡니다.
 */
public enum ExtractionStatus {

    // 아직 뽑지 않았음 — 지금은 쓰지 않음
    PENDING,

    // 뽑았음
    DONE,

    // 뽑다 실패했음 — 지금은 쓰지 않음
    //
    // 실패한 원문은 ingest 원문 상태가 FAILED 로 들고 있음
    FAILED
}
