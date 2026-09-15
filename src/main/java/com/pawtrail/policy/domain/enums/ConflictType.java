package com.pawtrail.policy.domain.enums;

/**
 * 어긋남의 종류입니다.
 *
 * 누가 찾아내는지가 값마다 다릅니다.
 */
public enum ConflictType {

    // 소스끼리 값이 갈림
    //
    // policy 가 병합하면서 스스로 찾음
    // 같은 장소의 pet_policy_source 행들을 나란히 놓고 필드별로 비교함
    CROSS_SOURCE,

    // 한 소스 안에서 값이 갈림
    //
    // extract 가 실어 보냄
    // 필드값과 본문이 서로 다른 말을 하는 경우이며 고캠핑에서 28건 확인됨
    //
    // policy 는 한 소스의 조건 한 벌만 받으므로 원본의 자기모순을 알 방법이 없음
    // extract 가 그 필드를 null 로 만들고 어긋났다는 사실만 넘김
    INTRA_SOURCE
}
