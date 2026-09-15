package com.pawtrail.policy.domain.enums;

/**
 * 조건을 어떻게 뽑았는지입니다.
 *
 * 정형 필드는 규칙으로 파싱하고 자유 텍스트만 LLM 을 태웁니다.
 * 전부 LLM 에 맡기지 않는 것은 이미 칸으로 들어 있는 값을 다시 추측하게 만들면
 * 정확한 값이 부정확해지기 때문입니다.
 */
public enum ExtractionMethod {

    // 규칙 파싱만으로 채움
    RULE,

    // 자유 텍스트를 LLM 으로 뽑음
    LLM,

    // 한 레코드 안에서 필드마다 갈림
    MIXED,

    // 사람이 넣음
    // 관리자 정정(MANUAL · OWNER)으로 들어온 행이 이 값임
    MANUAL
}
