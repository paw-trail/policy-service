package com.pawtrail.policy.infrastructure.persistence;

import com.pawtrail.policy.domain.model.PetPolicy;
import com.pawtrail.policy.domain.repository.PetPolicyRepository;
import com.pawtrail.policy.infrastructure.persistence.jpa.PetPolicyJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 */
@Repository
@RequiredArgsConstructor
public class PetPolicyRepositoryImpl implements PetPolicyRepository {

    private final PetPolicyJpaRepository petPolicyJpaRepository;

    @Override
    public PetPolicy save(PetPolicy policy) {
        return petPolicyJpaRepository.save(policy);
    }

    @Override
    public Optional<PetPolicy> findByPlaceId(UUID placeId) {
        return petPolicyJpaRepository.findById(placeId);
    }

    @Override
    public List<PetPolicy> findByPlaceIds(List<UUID> placeIds) {
        if (placeIds == null || placeIds.isEmpty()) {
            return List.of();
        }
        return petPolicyJpaRepository.findByPlaceIdIn(placeIds);
    }
}
