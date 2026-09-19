package com.pawtrail.policy.application.dto.output;

import com.pawtrail.policy.domain.enums.ExtractionMethod;
import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PolicyEvidence;

/**
 * 근거 한 줄을 내보내는 모양입니다.
 *
 * extract 가 보내는 EvidenceRequest 에 source 한 칸을 더한 것입니다.
 * 들어올 때와 나갈 때 이름과 뜻이 같아야 같은 근거를 같은 이름으로 부릅니다.
 * bulk 요청에서는 source 가 항목 쪽에 있어 근거마다 적지 않았습니다.
 *
 * @param fieldName    어느 조건의 근거인지. FieldSpec 의 조건 이름
 * @param source       어느 소스의 원문에서 나왔는지
 * @param originField  원문의 어느 필드에서 뽑았는지. 소스가 쓰는 이름 그대로
 * @param segmentIndex 그 필드 안에서 몇 번째 조각인지. 쪼갤 것이 없으면 null
 * @param segmentText  근거 문구 자체
 * @param extractionMethod 규칙이 읽었는지(RULE) 모델이 읽었는지(LLM).
 *                     verdict 가 판정 이유마다 "공공데이터 항목" 과 "안내문을 AI 가 읽음" 을 가름.
 *                     V25 이전에 들어와 아직 다시 뽑지 않은 근거는 null
 */
public record EvidenceOutput(
        String fieldName,
        SourceType source,
        String originField,
        Integer segmentIndex,
        String segmentText,
        ExtractionMethod extractionMethod
) {

    public static EvidenceOutput from(PolicyEvidence evidence) {
        return new EvidenceOutput(
                evidence.getFieldName(),
                evidence.getSource(),
                evidence.getOriginField(),
                evidence.getSegmentIndex(),
                evidence.getSegmentText(),
                evidence.getExtractionMethod()
        );
    }
}
