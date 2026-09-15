package com.pawtrail.policy.infrastructure.persistence;

import com.pawtrail.policy.domain.model.PolicyEvidence;
import com.pawtrail.policy.domain.repository.PolicyEvidenceRepository;
import com.pawtrail.policy.infrastructure.persistence.jpa.PolicyEvidenceJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 */
@Repository
@RequiredArgsConstructor
public class PolicyEvidenceRepositoryImpl implements PolicyEvidenceRepository {

    private final PolicyEvidenceJpaRepository policyEvidenceJpaRepository;

    @Override
    public List<PolicyEvidence> saveAll(List<PolicyEvidence> evidences) {
        return policyEvidenceJpaRepository.saveAll(evidences);
    }

    @Override
    public List<PolicyEvidence> findByPlaceId(UUID placeId) {
        return policyEvidenceJpaRepository.findByPlaceIdAndDeletedAtIsNull(placeId);
    }
}
