package com.pawtrail.policy.domain.enums;

/**
 * 조건을 어떻게 뽑았는지입니다.
 *
 * 정형 필드는 규칙으로 파싱하고 자유 텍스트만 LLM 을 태웁니다.
 * 전부 LLM 에 맡기지 않는 것은 이미 칸으로 들어 있는 값을 다시 추측하게 만들면
 * 정확한 값이 부정확해지기 때문입니다.
 *
 * 두 자리에 씁니다.
 * 출처 행(pet_policy_source)에는 넷 모두 들어가고, 근거 한 줄(policy_evidence)에는 RULE · LLM 만 들어갑니다.
 * 한 원문 안에서 칸마다 갈리면 출처 행은 MIXED 가 되어, 어느 근거를 모델이 읽었는지는 근거 줄이 말합니다.
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
