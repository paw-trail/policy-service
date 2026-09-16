package com.pawtrail.policy.infrastructure.persistence;

import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PolicyConflict;
import com.pawtrail.policy.domain.repository.PolicyConflictRepository;
import com.pawtrail.policy.infrastructure.persistence.jpa.PolicyConflictJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 */
@Repository
@RequiredArgsConstructor
public class PolicyConflictRepositoryImpl implements PolicyConflictRepository {

    private final PolicyConflictJpaRepository policyConflictJpaRepository;

    @Override
    public List<PolicyConflict> saveAll(List<PolicyConflict> conflicts) {
        return policyConflictJpaRepository.saveAll(conflicts);
    }

    @Override
    public List<PolicyConflict> findByPlaceId(UUID placeId) {
        return policyConflictJpaRepository.findByPlaceId(placeId);
    }

    @Override
    public int deleteIntraSource(UUID placeId, SourceType source) {
        return policyConflictJpaRepository.deleteIntraSource(placeId, source);
    }

    @Override
    public int deleteCrossSource(UUID placeId) {
        return policyConflictJpaRepository.deleteCrossSource(placeId);
    }
}
