package com.pawtrail.policy.domain.enums;

/**
 * 크기로 거는 제한입니다.
 *
 * 체중 상한(max_weight_kg)과 따로 있는 이유는 원문이 둘을 다르게 적기 때문입니다.
 * "10kg 이하" 는 체중이고 "소형견만" 은 크기입니다.
 * 둘 다 있는 곳도 있어 하나로 합치면 한쪽을 버리게 됩니다.
 */
public enum SizeRule {

    // 소형견만
    SMALL_ONLY,

    // 소형견과 중형견까지
    SMALL_MEDIUM,

    // 크기 제한 없음
    //
    // NULL 과 다름
    // NULL 은 원문에 크기 얘기가 없는 것이고, 이 값은 제한이 없다고 밝힌 것임
    ALL
}
