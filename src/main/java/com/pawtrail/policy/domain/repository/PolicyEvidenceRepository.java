package com.pawtrail.policy.domain.repository;

import com.pawtrail.policy.domain.model.PolicyEvidence;
import java.util.List;
import java.util.UUID;

/**
 * 근거 문구를 다루는 약속입니다.
 */
public interface PolicyEvidenceRepository {

    /**
     * 근거를 한꺼번에 남깁니다.
     *
     * 한 소스에서 조건 여러 개가 함께 나오므로 건별로 저장할 이유가 없습니다.
     */
    List<PolicyEvidence> saveAll(List<PolicyEvidence> evidences);

    /**
     * 이 장소의 근거를 전부 가져옵니다.
     *
     * 장소 상세의 확인 사항이 조건별로 근거를 붙여 보여줍니다.
     */
    List<PolicyEvidence> findByPlaceId(UUID placeId);
}
