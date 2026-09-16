package com.pawtrail.policy.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 조건 하나가 원문의 어느 문장에서 나왔는지입니다.
 *
 * @param fieldName    어느 조건의 근거인지. pet_policy 의 컬럼 이름
 * @param originField  원문의 어느 필드에서 뽑았는지. 소스가 쓰는 이름 그대로
 * @param segmentIndex 그 필드 안에서 몇 번째 조각인지. 쪼갤 것이 없으면 비움
 * @param segmentText  근거 문구 자체
 */
public record EvidenceRequest(

        @NotBlank(message = "근거의 조건 이름은 필수입니다.")
        @Size(max = 40)
        String fieldName,

        @NotBlank(message = "근거의 원문 필드는 필수입니다.")
        @Size(max = 40)
        String originField,

        Integer segmentIndex,

        @NotBlank(message = "근거 문구는 비어 있을 수 없습니다.")
        String segmentText
) {
}
