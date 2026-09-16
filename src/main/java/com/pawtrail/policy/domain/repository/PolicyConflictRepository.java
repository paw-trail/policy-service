package com.pawtrail.policy.domain.repository;

import com.pawtrail.policy.domain.enums.SourceType;
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

    /**
     * 한 소스가 남긴 소스 내 어긋남을 지웁니다.
     *
     * extract 가 실어 보내는 종류라 그 소스의 것만 갈아 끼웁니다.
     * 다른 소스의 INTRA_SOURCE 는 그 소스를 다시 뽑을 때 갈립니다.
     */
    int deleteIntraSource(UUID placeId, SourceType source);

    /**
     * 이 장소의 소스 간 어긋남을 지웁니다.
     *
     * 재병합이 place_id 기준으로 통째로 다시 만듭니다.
     * PolicyMerger 가 소스들을 나란히 놓고 계산해 내는 것이라
     * 소스 하나만 고쳐도 결과가 통째로 바뀌기 때문입니다.
     */
    int deleteCrossSource(UUID placeId);
}
