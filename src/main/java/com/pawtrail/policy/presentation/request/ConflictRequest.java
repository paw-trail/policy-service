package com.pawtrail.policy.presentation.request;

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
 * @param fieldName    어느 조건에서 갈렸는지
 * @param sourceValues 무엇과 무엇이 갈렸는지. 예 {"field": "가능", "text": "불가"}
 */
public record ConflictRequest(

        @NotBlank(message = "충돌의 조건 이름은 필수입니다.")
        @Size(max = 40)
        String fieldName,

        @NotEmpty(message = "어긋난 값은 비어 있을 수 없습니다.")
        Map<String, Object> sourceValues
) {
}
