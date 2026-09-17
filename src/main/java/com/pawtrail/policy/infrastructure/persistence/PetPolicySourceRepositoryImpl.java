package com.pawtrail.policy.infrastructure.persistence;

import com.pawtrail.policy.domain.enums.SourceType;
import com.pawtrail.policy.domain.model.PetPolicySource;
import com.pawtrail.policy.domain.repository.PetPolicySourceRepository;
import com.pawtrail.policy.infrastructure.persistence.jpa.PetPolicySourceJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 도메인이 선언한 약속을 스프링 데이터로 구현합니다.
 *
 * 소프트 딜리트 조건이 없습니다.
 * 이 표는 BaseEntity 를 상속하되 deleted_at 을 쓰지 않습니다.
 * 재추출도 관리자 정정도 그 소스의 행을 갱신하지 지우지 않기 때문입니다.
 */
@Repository
@RequiredArgsConstructor
public class PetPolicySourceRepositoryImpl implements PetPolicySourceRepository {

    private final PetPolicySourceJpaRepository petPolicySourceJpaRepository;

    @Override
    public PetPolicySource save(PetPolicySource source) {
        return petPolicySourceJpaRepository.save(source);
    }

    @Override
    public Optional<PetPolicySource> findByPlaceIdAndSource(UUID placeId, SourceType source) {
        return petPolicySourceJpaRepository
                .findByPlaceIdAndSource(placeId, source);
    }

    @Override
    public List<PetPolicySource> findByPlaceId(UUID placeId) {
        return petPolicySourceJpaRepository.findByPlaceId(placeId);
    }
}
