package com.pawtrail.policy.domain.enums;

/**
 * 견종으로 거는 제한입니다.
 *
 * 맹견은 동물보호법이 정한 목록이 있고 입마개 의무도 법으로 정해져 있습니다.
 * 장소가 그 위에 더 거는 제한만 여기 담습니다.
 */
public enum BreedRule {

    // 견종 제한 없음
    NONE,

    // 맹견은 입마개를 하면 됨
    DANGEROUS_MUZZLE,

    // 맹견은 입마개를 해도 안 됨
    DANGEROUS_BANNED
}
