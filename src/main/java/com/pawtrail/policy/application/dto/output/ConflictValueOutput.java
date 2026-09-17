package com.pawtrail.policy.application.dto.output;

import com.pawtrail.policy.domain.enums.SourceType;

/**
 * 충돌 한 줄 안에서 한 자리가 무엇이라고 했는지입니다.
 *
 * 소스 간 어긋남과 소스 내 어긋남이 같은 모양을 씁니다.
 * 화면은 "소스 이름(자리): 값" 한 가지로 그리면 됩니다.
 *
 * @param source 어느 소스인지. 코드만 보내며 화면이 장소 상세의 sources[] 로 이름을 붙임
 * @param origin 한 소스 안의 어느 자리인지. 소스 내 어긋남이면 "항목 값" · "본문", 소스 간 어긋남이면 null
 * @param value  사람이 읽는 값
 */
public record ConflictValueOutput(
        SourceType source,
        String origin,
        String value
) {
}
