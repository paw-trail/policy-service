package com.pawtrail.policy.application.dto.output;

/**
 * 청크 하나를 처리한 결과입니다.
 *
 * @param accepted 받아서 저장한 항목 수
 * @param merged   다시 합친 장소 수
 */
public record BulkUpsertResult(int accepted, int merged) {
}
