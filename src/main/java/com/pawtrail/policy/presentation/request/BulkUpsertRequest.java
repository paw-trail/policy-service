package com.pawtrail.policy.presentation.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * extract 가 한 청크를 보내는 요청입니다.
 *
 * 최상위를 배열이 아니라 객체로 둔 것은 배치 공통 값을 올릴 자리를 두기 위해서입니다.
 * 배열이면 나중에 배치 식별자 같은 것이 필요해질 때 계약이 깨집니다.
 *
 * 모델명과 프롬프트 판이 위에 있는 것은 한 배치에서 전부 같기 때문입니다.
 * 추출 방식만 건별로 두었습니다.
 *
 * @param extractedBy   LLM 을 태웠다면 어느 모델인지. 규칙 파싱만 했으면 비움
 * @param promptVersion 프롬프트 판. 고친 뒤 결과가 달라지면 어느 판으로 뽑은 것인지 가름
 * @param extractedAt   배치 시작 시각. 건별 시각이 의미를 갖는 자리가 없어 공통으로 둠
 */
public record BulkUpsertRequest(

        @Size(max = 50)
        String extractedBy,

        @Size(max = 20)
        String promptVersion,

        @NotNull(message = "추출 시각은 필수입니다.")
        LocalDateTime extractedAt,

        @NotEmpty(message = "적재할 항목이 없습니다.")
        @Size(max = 500, message = "한 번에 보낼 수 있는 항목은 500건입니다.")
        @Valid
        List<BulkItemRequest> items
) {
}
