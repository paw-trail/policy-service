package com.pawtrail.policy.domain.repository;

import com.pawtrail.policy.domain.model.PetPolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 병합 최종본을 다루는 약속입니다.
 */
public interface PetPolicyRepository {

    PetPolicy save(PetPolicy policy);

    Optional<PetPolicy> findByPlaceId(UUID placeId);

    /**
     * 여러 장소의 조건을 한 번에 가져옵니다.
     *
     * verdict 가 검색 결과를 판정할 때 부르는 경로입니다.
     * 건마다 따로 물으면 목록 하나에 조회가 수십 번 나갑니다.
     */
    List<PetPolicy> findByPlaceIds(List<UUID> placeIds);
}
