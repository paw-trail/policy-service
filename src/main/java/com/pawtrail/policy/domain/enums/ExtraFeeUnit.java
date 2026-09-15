package com.pawtrail.policy.domain.enums;

/**
 * 추가 요금의 단위입니다.
 *
 * 금액만 있고 단위가 없으면 사용자에게 보여줄 문장을 만들 수 없습니다.
 * 5,000원이 한 마리당인지 하룻밤당인지에 따라 실제 부담이 몇 배로 갈립니다.
 */
public enum ExtraFeeUnit {

    // 마리당
    PER_DOG,

    // 1박당
    PER_NIGHT,

    // 방문 1회당
    PER_VISIT
}
