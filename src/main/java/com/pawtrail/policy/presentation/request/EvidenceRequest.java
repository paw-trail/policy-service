package com.pawtrail.policy.presentation.request;

import com.pawtrail.policy.domain.rule.FieldSpec;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 조건 하나가 원문의 어느 문장에서 나왔는지입니다.
 *
 * <b>fieldName 은 조건 이름입니다.</b>
 * fields 의 칸 이름과 같은 camelCase(maxWeightKg)이며 DB 컬럼 이름(max_weight_kg)이 아닙니다.
 * 그 밖의 이름이 오면 400 입니다.
 * 막지 않으면 오타가 그대로 저장되고, batch 가 조건과 근거를 이름으로 이을 때
 * 오류 없이 그 칸의 근거만 사라집니다.
 *
 * @param fieldName    어느 조건의 근거인지. FieldSpec 의 조건 이름
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

    /**
     * 조건 이름이 스무 칸 중 하나인지 봅니다.
     *
     * 비어 있으면 통과시킵니다.
     * 그 경우는 @NotBlank 가 이미 막으므로 같은 자리에 오류가 둘 뜨지 않게 합니다.
     */
    @AssertTrue(message = "근거의 조건 이름이 조건 스무 칸에 없습니다.")
    public boolean isFieldNameKnown() {
        return fieldName == null || fieldName.isBlank() || FieldSpec.isKnownName(fieldName);
    }
}
