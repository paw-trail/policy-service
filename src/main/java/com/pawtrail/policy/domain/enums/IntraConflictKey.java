package com.pawtrail.policy.domain.enums;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 소스 내 어긋남이 담는 두 자리입니다.
 *
 * extract 는 한 소스 안에서 무엇과 무엇이 갈렸는지를 이 두 키로 보냅니다.
 * 공개 충돌 목록은 키를 사람이 읽는 말로 바꿔 내보냅니다.
 * bulk 요청 검증과 충돌 목록이 같은 어휘를 봐야 해 키와 말을 한곳에 둡니다.
 */
public enum IntraConflictKey {

    // 원문의 정형 항목 값
    FIELD("field", "항목 값"),

    // 원문의 본문 문장
    TEXT("text", "본문");

    private static final Set<String> KEYS = Arrays.stream(values())
            .map(IntraConflictKey::key)
            .collect(Collectors.toUnmodifiableSet());

    private final String key;
    private final String label;

    IntraConflictKey(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /**
     * 요청에 올 수 있는 키 전부입니다.
     */
    public static Set<String> keys() {
        return KEYS;
    }
}
