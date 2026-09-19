package com.pawtrail.policy.presentation.request;

import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.rule.FieldSpec;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * <b>추출 방식은 근거 줄마다 받습니다.</b>
 * 항목의 extractionMethod 는 출처 행 단위라, 한 원문 안에서 칸마다 규칙과 모델이 갈리면 MIXED 가 되어
 * 어느 근거를 모델이 읽었는지 알 수 없습니다. 빠진 채 받으면 판정 화면이 그 근거의 출처를 못 밝힙니다.
 *
 * @param fieldName    어느 조건의 근거인지. FieldSpec 의 조건 이름
 * @param originField  원문의 어느 필드에서 뽑았는지. 소스가 쓰는 이름 그대로
 * @param segmentIndex 그 필드 안에서 몇 번째 조각인지. 쪼갤 것이 없으면 비움
 * @param segmentText  근거 문구 자체
 * @param extractionMethod 이 근거를 규칙이 읽었는지(RULE) 모델이 읽었는지(LLM).
 *                     판정 화면이 이유마다 출처를 가르는 재료라 반드시 받음
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
        String segmentText,

        @NotNull(message = "근거의 추출 방식은 필수입니다.")
        ExtractionMethod extractionMethod
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

    /**
     * 근거 줄의 추출 방식이 RULE · LLM 가운데 하나인지 봅니다.
     *
     * MIXED 는 한 원문 안에서 칸마다 갈렸다는 출처 행의 값이라 근거 한 줄에는 뜻이 없고,
     * MANUAL 은 관리자 정정이라 근거 없이 들어옵니다.
     *
     * 비어 있으면 통과시킵니다.
     * 그 경우는 @NotNull 이 이미 막으므로 같은 자리에 오류가 둘 뜨지 않게 합니다.
     */
    @AssertTrue(message = "근거의 추출 방식은 RULE · LLM 만 받습니다.")
    public boolean isExtractionMethodAllowed() {
        return extractionMethod == null
                || extractionMethod == ExtractionMethod.RULE
                || extractionMethod == ExtractionMethod.LLM;
    }
}
