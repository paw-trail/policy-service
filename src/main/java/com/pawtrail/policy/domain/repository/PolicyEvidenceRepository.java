package com.pawtrail.policy.domain.repository;

import com.pawtrail.policy.domain.enums.SourceType;
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

    /**
     * 여러 장소의 근거를 한 번에 가져옵니다.
     *
     * batch 조회가 부르는 경로입니다.
     * 장소마다 따로 물으면 목록 하나에 조회가 수백 번 나갑니다.
     * idx_policy_evidence_place(place_id, field_name) 의 앞 칸으로 찾습니다.
     */
    List<PolicyEvidence> findByPlaceIds(List<UUID> placeIds);

    /**
     * 한 소스가 남긴 근거를 지웁니다.
     *
     * 재추출로 근거가 달라지면 갈아 끼웁니다.
     * 소프트 딜리트를 쓰지 않으므로 실제로 지우고 새로 넣습니다.
     *
     * 소스별로 지우는 것은 근거가 소스별로 뚜렷이 갈리기 때문입니다.
     * 장소 전체를 지우면 다른 소스의 근거까지 없어지는데, 그것은 지금 들어온
     * 요청에 없으므로 되살릴 방법이 없습니다.
     *
     * 지우기와 넣기를 한 트랜잭션으로 묶어야 합니다.
     * 그러지 않으면 지우기만 하고 넣기가 실패했을 때 근거가 통째로 비어
     * 그 장소의 판정에 이유가 사라집니다.
     */
    int deleteByPlaceIdAndSource(UUID placeId, SourceType source);
}
