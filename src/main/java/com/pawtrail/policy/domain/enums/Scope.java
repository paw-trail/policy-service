package com.pawtrail.policy.domain.enums;

/**
 * 장소 전체가 되는지 일부 구역만 되는지입니다.
 *
 * 판정의 첫 축입니다.
 * 여기가 갈리면 다른 조건을 다 만족해도 결과가 달라집니다.
 */
public enum Scope {

    // 장소 전체에서 동반할 수 있음
    ALL_AREA,

    // 일부 구역만 됨
    //
    // 공사 데이터에서 40% 가 이 값임
    // 어느 구역인지는 excluded_zones · allowed_zones_only 가 담음
    PARTIAL,

    // 원문에 범위를 가를 근거가 없음
    //
    // NULL 과 다름
    // NULL 은 이 필드를 아예 못 뽑은 것이고, 이 값은 뽑아 봤으나 모른다는 뜻임
    UNKNOWN
}
