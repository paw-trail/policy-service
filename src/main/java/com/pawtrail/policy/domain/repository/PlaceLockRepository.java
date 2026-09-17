package com.pawtrail.policy.domain.repository;

import java.util.UUID;

/**
 * 한 장소의 조건을 바꾸는 작업을 한 줄로 세우는 약속입니다.
 *
 * 병합은 소스 행들을 읽어 결과를 계산한 뒤 pet_policy 에 씁니다.
 * 같은 장소를 두 트랜잭션이 동시에 병합하면, 먼저 읽은 쪽이 나중에 커밋하면서
 * 상대가 넣은 행을 보지 못한 결과로 덮을 수 있습니다.
 * <pre>
 * 적재와 관리자 정정이 겹침   정정 행이 있는데 조건은 공공 값 그대로 남음
 * 정정끼리 겹침             OWNER 행이 있는데 MANUAL 이 이긴 것으로 남음
 * </pre>
 *
 * 잠금은 트랜잭션이 끝날 때 풀립니다. 같은 트랜잭션 안에서 다시 잡아도 막히지 않습니다.
 */
public interface PlaceLockRepository {

    /**
     * 그 장소의 잠금을 잡습니다. 다른 트랜잭션이 잡고 있으면 끝날 때까지 기다립니다.
     *
     * 트랜잭션 안에서만 부를 수 있습니다.
     */
    void lock(UUID placeId);
}
