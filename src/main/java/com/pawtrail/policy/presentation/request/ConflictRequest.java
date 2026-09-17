package com.pawtrail.policy.presentation.request;

import com.pawtrail.policy.domain.enums.IntraConflictKey;
import com.pawtrail.policy.domain.rule.FieldSpec;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 한 소스 안에서 앞뒤가 안 맞는 자리입니다.
 *
 * extract 만 볼 수 있는 것이라 실어 보냅니다.
 * policy 는 한 소스의 조건 한 벌만 받으므로 원본의 자기모순을 알 방법이 없습니다.
 * 고캠핑에서 필드값과 본문이 다른 말을 하는 경우가 28건 확인됐습니다.
 *
 * 소스 간 어긋남(CROSS_SOURCE)은 여기로 오지 않습니다.
 * 그것은 policy 가 소스들을 나란히 놓고 비교해 스스로 만듭니다.
 *
 * fieldName 은 근거와 같은 규칙입니다. 조건 스무 칸의 이름(camelCase)이어야 하며
 * 병합이 만드는 소스 간 어긋남도 같은 이름으로 저장됩니다.
 *
 * sourceValues 는 field(원문의 항목 값)와 text(본문) 두 키로 보냅니다.
 * 공개 충돌 목록이 두 키를 "항목 값" · "본문" 으로 바꿔 보여 주므로 그 밖의 키는 400 입니다.
 *
 * @param fieldName    어느 조건에서 갈렸는지. FieldSpec 의 조건 이름
 * @param sourceValues 무엇과 무엇이 갈렸는지. 예 {"field": "가능", "text": "불가"}
 */
public record ConflictRequest(

        @NotBlank(message = "충돌의 조건 이름은 필수입니다.")
        @Size(max = 40)
        String fieldName,

        @NotEmpty(message = "어긋난 값은 비어 있을 수 없습니다.")
        Map<String, Object> sourceValues
) {

    /**
     * 조건 이름이 스무 칸 중 하나인지 봅니다.
     *
     * 비어 있으면 통과시킵니다. 그 경우는 @NotBlank 가 이미 막습니다.
     */
    @AssertTrue(message = "충돌의 조건 이름이 조건 스무 칸에 없습니다.")
    public boolean isFieldNameKnown() {
        return fieldName == null || fieldName.isBlank() || FieldSpec.isKnownName(fieldName);
    }

    /**
     * 무엇과 무엇이 갈렸는지를 정해진 두 키로 보냈는지 봅니다.
     *
     * 그 밖의 키가 오면 원문의 키 이름이 사용자 화면에 그대로 뜹니다.
     * 둘 중 하나만 오면 갈린 상대가 없습니다.
     * 비어 있으면 통과시킵니다. 그 경우는 @NotEmpty 가 이미 막습니다.
     */
    @AssertTrue(message = "소스 내 충돌의 값은 field 와 text 두 키로 보내야 합니다.")
    public boolean isSourceValueKeysValid() {
        return sourceValues == null || sourceValues.isEmpty()
                || IntraConflictKey.keys().equals(sourceValues.keySet());
    }
}
