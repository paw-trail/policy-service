package com.pawtrail.policy.domain.repository;

import com.pawtrail.policy.domain.model.PolicyCorrectionLog;
import java.util.List;
import java.util.UUID;

/**
 * 관리자 정정 이력을 다루는 약속입니다.
 *
 * 지우거나 고치는 메서드가 없습니다.
 * 한 번 쓰고 손대지 않는 것이 이 표의 뜻입니다.
 */
public interface PolicyCorrectionLogRepository {

    PolicyCorrectionLog save(PolicyCorrectionLog log);

    /**
     * 이 장소의 정정 이력을 최근 것부터 가져옵니다.
     *
     * idx_policy_correction_place(place_id, corrected_at DESC) 를 탑니다.
     */
    List<PolicyCorrectionLog> findByPlaceId(UUID placeId);
}
