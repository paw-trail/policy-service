package com.pawtrail.policy.domain.repository;

import com.pawtrail.policy.domain.model.PolicyConflict;
import java.util.List;
import java.util.UUID;

/**
 * 어긋난 자리를 다루는 약속입니다.
 */
public interface PolicyConflictRepository {

    List<PolicyConflict> saveAll(List<PolicyConflict> conflicts);

    /**
     * 이 장소의 충돌을 전부 가져옵니다.
     *
     * 닫힌 것도 함께 옵니다.
     * 관리자가 정정한 뒤에도 어긋났던 사실은 남아 있어야 왜 이 값이 됐는지를 되짚습니다.
     * 화면에 무엇을 보일지는 부르는 쪽이 가립니다.
     */
    List<PolicyConflict> findByPlaceId(UUID placeId);
}
