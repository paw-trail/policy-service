package com.pawtrail.policy.infrastructure.persistence;

import com.pawtrail.policy.domain.model.PolicyCorrectionLog;
import com.pawtrail.policy.domain.repository.PolicyCorrectionLogRepository;
import com.pawtrail.policy.infrastructure.persistence.jpa.PolicyCorrectionLogJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 */
@Repository
@RequiredArgsConstructor
public class PolicyCorrectionLogRepositoryImpl implements PolicyCorrectionLogRepository {

    private final PolicyCorrectionLogJpaRepository policyCorrectionLogJpaRepository;

    @Override
    public PolicyCorrectionLog save(PolicyCorrectionLog log) {
        return policyCorrectionLogJpaRepository.save(log);
    }

    @Override
    public List<PolicyCorrectionLog> findByPlaceId(UUID placeId) {
        return policyCorrectionLogJpaRepository.findByPlaceIdOrderByCorrectedAtDesc(placeId);
    }
}
