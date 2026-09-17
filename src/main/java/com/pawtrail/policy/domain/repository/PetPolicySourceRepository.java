package com.pawtrail.policy.domain.repository;

import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicySource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 소스별 추출 결과를 다루는 약속입니다.
 */
public interface PetPolicySourceRepository {

    PetPolicySource save(PetPolicySource source);

    /**
     * 이 장소의 이 소스가 이미 있는지 찾습니다.
     *
     * 적재가 새로 만들지 갱신할지를 이것으로 가릅니다.
     * uq_policy_source_place(place_id, source) 가 걸려 있어 있어도 한 행입니다.
     */
    Optional<PetPolicySource> findByPlaceIdAndSource(UUID placeId, SourceType source);

    /**
     * 이 장소에 대해 소스들이 말한 것을 전부 가져옵니다.
     *
     * 병합의 입력입니다.
     * 우선순위를 가리려면 어느 소스가 있는지를 다 봐야 하므로 목록으로 받습니다.
     */
    List<PetPolicySource> findByPlaceId(UUID placeId);
}
